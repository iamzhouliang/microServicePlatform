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

package com.microservice.framework.ai.harness.tool;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 仅在后续 HTTP 用户轮暴露确认工具，阻止模型在首次写调用的同一轮自我确认。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class PendingActionToolProvider implements ToolProvider {
    
    public static final String CONFIRM_TOOL = "confirm_pending_action";
    
    private final HarnessOperationStore store;
    private final PendingActionService pendingActionService;
    
    public PendingActionToolProvider(HarnessOperationStore store, PendingActionService pendingActionService) {
        this.store = Objects.requireNonNull(store, "Operation Store 不能为空");
        this.pendingActionService = Objects.requireNonNull(pendingActionService, "待处理操作服务不能为空");
    }
    
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        Optional<HarnessInvocation> invocation = HarnessInvocation.from(request.invocationContext());
        if (invocation.isEmpty()) {
            return ToolProviderResult.builder().build();
        }
        HarnessInvocation actor = invocation.orElseThrow();
        Optional<HarnessOperation> pending = store.findPending(actor.tenantId(), actor.userId(),
                actor.conversationId());
        if (pending.isEmpty() || pending.orElseThrow().status() != HarnessOperationStatus.WAITING_CONFIRMATION
                || pending.orElseThrow().initialTurnId().equals(actor.turnId())
                || !pending.orElseThrow().operationId().equals(actor.operationId())) {
            return ToolProviderResult.builder().build();
        }
        ToolSpecification specification = ToolSpecification.builder().name(CONFIRM_TOOL)
                .description("当用户在当前消息中明确同意上一轮待处理操作时调用")
                .parameters(JsonObjectSchema.builder().build()).build();
        HarnessOperation operation = pending.orElseThrow();
        ToolExecutor executor = new ToolExecutor() {
            
            @Override
            public String execute(ToolExecutionRequest toolRequest, Object memoryId) {
                return "确认失败：缺少可信调用身份";
            }
            
            @Override
            public ToolExecutionResult executeWithContext(ToolExecutionRequest toolRequest,
                                                          dev.langchain4j.invocation.InvocationContext context) {
                HarnessInvocation current = HarnessInvocation.from(context)
                        .orElseThrow(() -> new IllegalArgumentException("确认操作缺少可信调用身份"));
                pendingActionService.confirm(current, operation.operationId());
                String message = "用户已确认上一轮待处理操作，可以继续执行";
                return ToolExecutionResult.builder().result(message).resultText(message)
                        .attributes(Map.of("confirmed_operation", operation.operationId())).build();
            }
        };
        return ToolProviderResult.builder().add(specification, executor).build();
    }
    
    @Override
    public boolean isDynamic() {
        return true;
    }
}
