/*
 * Copyright (c) 2023 MICRO-SERVICE-PLATFORM Authors. All Rights Reserved.
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microservice.framework.ai.harness.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class HarnessOperationCoordinatorTest {
    
    @Test
    void shouldPersistIntentAndLeaseBeforeInvokingWriteTool() {
        RecordingStore store = new RecordingStore();
        HarnessOperationCoordinator coordinator = coordinator(store, ignored -> Optional.empty());
        ToolExecutor delegate = new ToolExecutor() {
            
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "unused";
            }
            
            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request,
                                                          InvocationContext context) {
                store.addEvent("delegate");
                return ToolExecutionResult.builder().result("创建成功").resultText("创建成功").build();
            }
        };
        
        ToolExecutionResult result = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        
        assertThat(result.resultText()).isEqualTo("创建成功");
        assertThat(store.events()).containsExactly("intent", "lease", "delegate", "success");
        assertThat(store.operation().status()).isEqualTo(HarnessOperationStatus.SUCCEEDED);
        assertThat(store.operation().fencingToken()).isPositive();
    }
    
    @Test
    void shouldVerifyUnknownResultInsteadOfBlindRetrying() {
        RecordingStore store = new RecordingStore();
        HarnessOperationCoordinator coordinator = coordinator(store,
                ignored -> Optional.of(operation -> HarnessVerification.confirmed("已通过权威回读确认成功")));
        int[] invocations = {0};
        ToolExecutor delegate = (request, memoryId) -> {
            invocations[0]++;
            throw new UncertainToolExecutionException("远程调用超时，结果未知");
        };
        
        ToolExecutionResult result = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        
        assertThat(invocations[0]).isEqualTo(1);
        assertThat(store.events()).containsExactly("intent", "lease", "unknown", "success");
        assertThat(result.resultText()).contains("权威回读确认成功");
        assertThat(store.operation().status()).isEqualTo(HarnessOperationStatus.SUCCEEDED);
    }
    
    @Test
    void shouldKeepUnknownResultAndNeverReplayWriteWhenVerifierCannotConfirm() {
        RecordingStore store = new RecordingStore();
        HarnessOperationCoordinator coordinator = coordinator(store,
                ignored -> Optional.of(operation -> HarnessVerification.unconfirmed("权威回读暂时不可用")));
        int[] invocations = {0};
        ToolExecutor delegate = (request, memoryId) -> {
            invocations[0]++;
            throw new UncertainToolExecutionException("远程调用超时，结果未知");
        };
        
        ToolExecutionResult first = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        ToolExecutionResult second = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        
        assertThat(invocations[0]).isEqualTo(1);
        assertThat(first.isError()).isTrue();
        assertThat(second.isError()).isTrue();
        assertThat(second.resultText()).contains("工具执行结果无法确认", "权威回读暂时不可用");
        assertThat(store.operation().status()).isEqualTo(HarnessOperationStatus.RESULT_UNKNOWN);
    }
    
    @Test
    void shouldPersistToolErrorAsFailedAndReplayErrorWithoutInvokingDelegateAgain() {
        RecordingStore store = new RecordingStore();
        HarnessOperationCoordinator coordinator = coordinator(store, ignored -> Optional.empty());
        int[] invocations = {0};
        ToolExecutor delegate = new ToolExecutor() {
            
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "unused";
            }
            
            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request,
                                                          InvocationContext context) {
                invocations[0]++;
                return ToolExecutionResult.builder().isError(true)
                        .result("账号已存在").resultText("账号已存在").build();
            }
        };
        
        ToolExecutionResult first = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        ToolExecutionResult replay = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        
        assertThat(invocations[0]).isEqualTo(1);
        assertThat(first.isError()).isTrue();
        assertThat(replay.isError()).isTrue();
        assertThat(replay.resultText()).isEqualTo("账号已存在");
        assertThat(store.operation().status()).isEqualTo(HarnessOperationStatus.FAILED);
    }
    
    @Test
    void shouldMarkResultUnknownWhenLocalCommitFailsAfterDelegateReturned() {
        RecordingStore store = new RecordingStore();
        store.failNextSuccessSave();
        HarnessOperationCoordinator coordinator = coordinator(store,
                ignored -> Optional.of(operation -> HarnessVerification.unconfirmed("权威回读暂时不可用")));
        int[] invocations = {0};
        ToolExecutor delegate = new ToolExecutor() {
            
            @Override
            public String execute(ToolExecutionRequest request, Object memoryId) {
                return "unused";
            }
            
            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest request,
                                                          InvocationContext context) {
                invocations[0]++;
                return ToolExecutionResult.builder().result("创建成功").resultText("创建成功").build();
            }
        };
        
        ToolExecutionResult result = coordinator.execute(governance(false, false), request(), context("turn-1"),
                delegate);
        
        assertThat(invocations[0]).isEqualTo(1);
        assertThat(result.isError()).isTrue();
        assertThat(store.operation().status()).isEqualTo(HarnessOperationStatus.RESULT_UNKNOWN);
        assertThat(store.operation().errorMessage()).contains("本地结果提交失败");
    }
    
    private static HarnessOperationCoordinator coordinator(RecordingStore store,
                                                           HarnessVerifierResolver verifierResolver) {
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC);
        PendingActionService pending = new PendingActionService(store, clock);
        return new HarnessOperationCoordinator(store, pending, verifierResolver, clock, Duration.ofSeconds(30));
    }
    
    private static ToolGovernance governance(boolean confirmation, boolean approval) {
        return new ToolGovernance("provision_user", "module:iam:user-management", "iam", "1.0.0",
                "iam:user:create",
                HarnessRiskLevel.HIGH, true, true, confirmation, approval, "verify_user", null);
    }
    
    private static ToolExecutionRequest request() {
        return ToolExecutionRequest.builder().id("call-1").name("provision_user")
                .arguments("{\"username\":\"zhangsan\"}").build();
    }
    
    static InvocationContext context(String turnId) {
        HarnessInvocation invocation = new HarnessInvocation("1", "10", "conversation-1", "operation-1",
                turnId, Set.of("iam:user:create"));
        return InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1")
                .invocationParameters(InvocationParameters.from(HarnessInvocation.PARAMETER_KEY, invocation))
                .build();
    }
    
    static final class RecordingStore implements HarnessOperationStore {
        
        private final List<String> events = new ArrayList<>();
        private HarnessOperation operation;
        private boolean failNextSuccessSave;
        
        void addEvent(String event) {
            events.add(event);
        }
        
        List<String> events() {
            return events;
        }
        
        void clearEvents() {
            events.clear();
        }
        
        HarnessOperation operation() {
            return operation;
        }
        
        void failNextSuccessSave() {
            failNextSuccessSave = true;
        }
        
        @Override
        public HarnessOperation recordIntent(HarnessOperation intent) {
            events.add("intent");
            if (operation == null) {
                operation = intent;
            }
            return operation;
        }
        
        @Override
        public Optional<HarnessOperation> find(String tenantId, String operationId) {
            return Optional.ofNullable(operation).filter(value -> value.tenantId().equals(tenantId)
                    && value.operationId().equals(operationId));
        }
        
        @Override
        public HarnessOperation claimLease(HarnessOperation value, String leaseOwner, Instant expiresAt) {
            events.add("lease");
            operation = value.withLease(leaseOwner, expiresAt, value.fencingToken() + 1);
            return operation;
        }
        
        @Override
        public HarnessOperation save(HarnessOperation value) {
            if (value.status() == HarnessOperationStatus.SUCCEEDED && failNextSuccessSave) {
                failNextSuccessSave = false;
                throw new IllegalStateException("模拟本地结果提交失败");
            }
            operation = value;
            String event = switch (value.status()) {
                case SUCCEEDED -> "success";
                case RESULT_UNKNOWN -> "unknown";
                case WAITING_CONFIRMATION -> "waiting_confirmation";
                case WAITING_APPROVAL -> "waiting_approval";
                default -> value.status().name().toLowerCase();
            };
            events.add(event);
            return value;
        }
    }
}
