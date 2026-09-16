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

import com.microservice.framework.ai.harness.observability.HarnessTraceEvent;
import com.microservice.framework.ai.harness.observability.HarnessTraceRecorder;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.ai.harness.spill.ToolResultSpillProjector;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import java.util.Map;

/**
 * 执行写 Tool 的持久化 Operation/Saga 协调器。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class HarnessOperationCoordinator {
    
    private final HarnessOperationStore store;
    private final PendingActionService pendingActionService;
    private final HarnessVerifierResolver verifierResolver;
    private final Clock clock;
    private final Duration leaseDuration;
    private final HarnessTraceRecorder traceRecorder;
    private final ToolResultSpillProjector spillProjector;
    private final ApprovalSummaryProjector approvalSummaryProjector;
    
    public HarnessOperationCoordinator(HarnessOperationStore store, PendingActionService pendingActionService,
                                       HarnessVerifierResolver verifierResolver, Clock clock, Duration leaseDuration) {
        this(store, pendingActionService, verifierResolver, clock, leaseDuration, HarnessTraceRecorder.noop());
    }
    
    public HarnessOperationCoordinator(HarnessOperationStore store, PendingActionService pendingActionService,
                                       HarnessVerifierResolver verifierResolver, Clock clock, Duration leaseDuration,
                                       HarnessTraceRecorder traceRecorder) {
        this(store, pendingActionService, verifierResolver, clock, leaseDuration, traceRecorder, null);
    }
    
    public HarnessOperationCoordinator(HarnessOperationStore store, PendingActionService pendingActionService,
                                       HarnessVerifierResolver verifierResolver, Clock clock, Duration leaseDuration,
                                       HarnessTraceRecorder traceRecorder, ToolResultSpillProjector spillProjector) {
        this(store, pendingActionService, verifierResolver, clock, leaseDuration, traceRecorder, spillProjector,
                ApprovalSummaryProjector.generic());
    }
    
    public HarnessOperationCoordinator(HarnessOperationStore store, PendingActionService pendingActionService,
                                       HarnessVerifierResolver verifierResolver, Clock clock, Duration leaseDuration,
                                       HarnessTraceRecorder traceRecorder, ToolResultSpillProjector spillProjector,
                                       ApprovalSummaryProjector approvalSummaryProjector) {
        this.store = Objects.requireNonNull(store, "Operation Store 不能为空");
        this.pendingActionService = Objects.requireNonNull(pendingActionService, "待处理操作服务不能为空");
        this.verifierResolver = Objects.requireNonNull(verifierResolver, "Verifier 解析器不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.leaseDuration = Objects.requireNonNull(leaseDuration, "租约时长不能为空");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "Trace Recorder 不能为空");
        this.spillProjector = spillProjector;
        this.approvalSummaryProjector = Objects.requireNonNull(approvalSummaryProjector, "审批投影器不能为空");
    }
    
    /**
     * 按 intent、门控、租约、真实执行、校验的顺序执行写操作。
     *
     * @param governance Tool 治理规则
     * @param request LangChain4j Tool 调用请求
     * @param context LangChain4j 调用上下文
     * @param delegate 官方 ToolExecutor
     * @return 受治理后的 Tool 执行结果
     */
    public ToolExecutionResult execute(ToolGovernance governance, ToolExecutionRequest request,
                                       InvocationContext context, ToolExecutor delegate) {
        HarnessInvocation invocation = HarnessInvocation.from(context)
                .orElseThrow(() -> new IllegalArgumentException("缺少可信调用身份"))
                .forToolCall(request.id());
        Instant now = clock.instant();
        var approvalSummary = approvalSummaryProjector.project(governance, request);
        Instant approvalExpiresAt = governance.approvalRequired() ? now.plus(Duration.ofHours(24)) : null;
        HarnessOperation intent = HarnessOperation.intent(invocation, governance, request.id(),
                sha256(request.arguments()), approvalSummary, approvalExpiresAt, now);
        HarnessOperation operation = store.recordIntent(intent);
        Instant executionStartedAt = clock.instant();
        safeTrace(operation, "TOOL_INTENT_RECORDED", operation.status().name(), executionStartedAt);
        validateReplay(operation, intent);
        if (operation.status() == HarnessOperationStatus.SUCCEEDED) {
            return success(operation.resultText());
        }
        if (operation.status() == HarnessOperationStatus.FAILED) {
            return error(operation.errorMessage());
        }
        if (operation.status() == HarnessOperationStatus.RESULT_UNKNOWN) {
            return reconcile(governance, operation);
        }
        
        PendingActionService.Gate gate = pendingActionService.evaluate(invocation, governance);
        if (!gate.ready()) {
            pendingActionService.markWaiting(invocation, gate);
            safeTrace(operation, "TOOL_WAITING", gate.status().name(), executionStartedAt);
            return error(gate.message());
        }
        
        String leaseOwner = invocation.turnId() + ":" + request.id();
        operation = store.claimLease(operation, leaseOwner, clock.instant().plus(leaseDuration));
        final ToolExecutionResult result;
        try {
            result = delegate.executeWithContext(request, context);
        } catch (UncertainToolExecutionException uncertain) {
            HarnessOperation unknown = store.save(operation.resultUnknown(uncertain.getMessage(), clock.instant()));
            safeTrace(operation, "TOOL_RESULT_UNKNOWN", "RESULT_UNKNOWN", executionStartedAt);
            return reconcile(governance, unknown);
        } catch (RuntimeException failure) {
            store.save(operation.failed(chineseMessage(failure), clock.instant()));
            safeTrace(operation, "TOOL_FAILED", "FAILED", executionStartedAt);
            return error("工具执行失败：" + chineseMessage(failure));
        }
        
        if (result.isError()) {
            String message = Objects.requireNonNullElse(result.resultText(), "工具返回失败结果");
            store.save(operation.failed(message, clock.instant()));
            safeTrace(operation, "TOOL_FAILED", "FAILED", executionStartedAt);
            return error(message);
        }
        
        try {
            ToolExecutionResult projected = projectResult(invocation, operation, result);
            store.save(operation.succeeded(projected.resultText(), clock.instant()));
            safeTrace(operation, "TOOL_COMPLETED", "SUCCEEDED", executionStartedAt);
            return projected;
        } catch (RuntimeException localFailure) {
            String message = "外部工具已返回成功，但本地结果提交失败：" + chineseMessage(localFailure);
            try {
                HarnessOperation unknown = store.save(operation.resultUnknown(message, clock.instant()));
                safeTrace(unknown, "TOOL_RESULT_UNKNOWN", "RESULT_UNKNOWN", executionStartedAt);
                return reconcile(governance, unknown);
            } catch (RuntimeException persistenceFailure) {
                safeTrace(operation, "TOOL_RESULT_UNKNOWN", "RESULT_UNKNOWN", executionStartedAt);
                return error("工具执行结果未知，本地状态提交失败，已停止自动重试");
            }
        }
    }
    
    private ToolExecutionResult reconcile(ToolGovernance governance, HarnessOperation operation) {
        Optional<HarnessVerifier> verifier = verifierResolver.resolve(governance);
        if (verifier.isEmpty()) {
            return error("工具执行结果未知，且未配置权威校验器，已停止自动重试");
        }
        HarnessVerification verification = verifier.orElseThrow().verify(operation);
        if (!verification.confirmed()) {
            // 外部写是否成功仍不确定，必须保留未知状态以阻止后续重放写操作。
            String reason = operation.errorMessage() == null || operation.errorMessage().isBlank()
                    ? verification.message()
                    : operation.errorMessage() + "；对账结果：" + verification.message();
            store.save(operation.resultUnknown(reason, clock.instant()));
            return error("工具执行结果无法确认：" + verification.message());
        }
        store.save(operation.succeeded(verification.message(), clock.instant()));
        safeTrace(operation, "TOOL_RECONCILED", "SUCCEEDED", operation.updatedAt());
        return success(verification.message());
    }
    
    /**
     * 由人工控制面触发结果未知对账；只允许原操作人在当前权限仍有效时执行。
     *
     * @param actor 原操作人当前身份
     * @param governance Tool 当前治理规则
     * @param operationId 待对账 Operation 标识
     * @return 权威对账后的 Tool 结果
     * @throws IllegalArgumentException 当身份、权限、会话、状态或治理身份不匹配时抛出
     */
    public ToolExecutionResult reconcile(HarnessInvocation actor, ToolGovernance governance, String operationId) {
        HarnessOperation operation = store.find(actor.tenantId(), operationId)
                .orElseThrow(() -> new IllegalArgumentException("待对账 Operation 不存在"));
        if (!operation.actorUserId().equals(actor.userId())) {
            throw new IllegalArgumentException("只有原操作人可以发起人工对账");
        }
        if (!operation.conversationId().equals(actor.conversationId())) {
            throw new IllegalArgumentException("待对账 Operation 不属于当前会话");
        }
        if (operation.status() != HarnessOperationStatus.RESULT_UNKNOWN) {
            throw new IllegalArgumentException("只有执行结果未知的 Operation 可以人工对账");
        }
        if (!operation.toolIdentity().equals(governance.identity())) {
            throw new IllegalArgumentException("Operation 的 Tool 治理身份已发生变化");
        }
        if (!hasRequiredPermissions(actor, governance.requiredPermission())) {
            throw new IllegalArgumentException("原操作人的权限已发生变化，已拒绝人工对账");
        }
        return reconcile(governance, operation);
    }
    
    private void safeTrace(HarnessOperation operation, String type, String status, Instant startedAt) {
        try {
            traceRecorder.record(HarnessTraceEvent.create(operation.operationId(), operation.toolCallId(), type,
                    status, clock.instant(), Duration.between(startedAt, clock.instant()), 0, List.of(),
                    Map.of("toolIdentity", operation.toolIdentity())));
        } catch (RuntimeException ignored) {
            // Trace 属于旁路观测，失败不能改变 Tool 的业务状态。
        }
    }
    
    private ToolExecutionResult projectResult(HarnessInvocation invocation, HarnessOperation operation,
                                              ToolExecutionResult result) {
        if (spillProjector == null) {
            return result;
        }
        ToolResultSpillProjector.TextProjection projection = spillProjector.projectText(
                invocation.tenantId(), operation.operationId(), operation.toolCallId(), result.resultText());
        if (!projection.spilled()) {
            return result;
        }
        Map<String, Object> attributes = new java.util.LinkedHashMap<>(result.attributes());
        attributes.put("evidenceReference", projection.evidenceReference());
        return ToolExecutionResult.builder().isError(result.isError())
                .result(projection.resultText()).resultText(projection.resultText())
                .attributes(Map.copyOf(attributes)).build();
    }
    
    private static void validateReplay(HarnessOperation stored, HarnessOperation intent) {
        if (!stored.toolsetId().equals(intent.toolsetId())
                || !stored.toolIdentity().equals(intent.toolIdentity())
                || !stored.argumentsDigest().equals(intent.argumentsDigest())) {
            throw new IllegalArgumentException("相同 operationId 的工具或参数已发生变化，拒绝幂等重放");
        }
    }
    
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Objects.requireNonNullElse(value, "{}").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", impossible);
        }
    }
    
    private static String chineseMessage(RuntimeException failure) {
        return failure.getMessage() == null || failure.getMessage().isBlank()
                ? "业务系统返回了未说明的异常"
                : failure.getMessage();
    }
    
    private static boolean hasRequiredPermissions(HarnessInvocation invocation, String expression) {
        if ("*".equals(expression)) {
            return true;
        }
        return java.util.Arrays.stream(expression.split(","))
                .map(String::trim).filter(permission -> !permission.isEmpty())
                .allMatch(invocation.permissions()::contains);
    }
    
    private static ToolExecutionResult success(String message) {
        return ToolExecutionResult.builder().result(message).resultText(message).build();
    }
    
    private static ToolExecutionResult error(String message) {
        return ToolExecutionResult.builder().isError(true).result(message).resultText(message).build();
    }
}
