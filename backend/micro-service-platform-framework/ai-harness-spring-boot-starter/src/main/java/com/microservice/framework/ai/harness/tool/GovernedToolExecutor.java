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
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.Objects;
import java.util.Optional;

/**
 * 在官方 ToolExecutor 外围执行企业治理，并把真实执行继续委托给 LangChain4j。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class GovernedToolExecutor implements ToolExecutor {
    
    private final Optional<ToolGovernance> governance;
    private final ToolExecutor delegate;
    private final HarnessOperationCoordinator operationCoordinator;
    
    public GovernedToolExecutor(ToolGovernance governance, ToolExecutor delegate) {
        this(governance, delegate, null);
    }
    
    public GovernedToolExecutor(ToolGovernance governance, ToolExecutor delegate,
                                HarnessOperationCoordinator operationCoordinator) {
        this(Optional.of(Objects.requireNonNull(governance, "工具治理元数据不能为空")), delegate,
                operationCoordinator);
    }
    
    private GovernedToolExecutor(Optional<ToolGovernance> governance, ToolExecutor delegate,
                                 HarnessOperationCoordinator operationCoordinator) {
        this.governance = Objects.requireNonNull(governance, "工具治理元数据不能为空");
        this.delegate = Objects.requireNonNull(delegate, "ToolExecutor 委托不能为空");
        this.operationCoordinator = operationCoordinator;
    }
    
    /**
     * 为未知动态工具创建默认拒绝执行器。
     *
     * @param delegate 原始 ToolExecutor
     * @return 默认拒绝未知 Tool 的受治理执行器
     */
    public static GovernedToolExecutor unregistered(ToolExecutor delegate) {
        return new GovernedToolExecutor(Optional.empty(), delegate, null);
    }
    
    @Override
    public String execute(ToolExecutionRequest request, Object memoryId) {
        return "工具执行失败：缺少可信调用身份，已拒绝执行";
    }
    
    @Override
    public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
        if (governance.isEmpty()) {
            return error("工具执行失败：缺少治理元数据，已拒绝未知工具");
        }
        Optional<HarnessInvocation> invocation = HarnessInvocation.from(context);
        if (invocation.isEmpty()) {
            return error("工具执行失败：缺少可信调用身份，已拒绝执行");
        }
        ToolGovernance rule = governance.orElseThrow();
        if (!hasRequiredPermissions(invocation.orElseThrow(), rule.requiredPermission())) {
            return error("工具执行失败：您没有执行该操作的权限");
        }
        if (rule.write() && operationCoordinator != null) {
            return operationCoordinator.execute(rule, request, context, delegate);
        }
        return delegate.executeWithContext(request, context);
    }
    
    private static ToolExecutionResult error(String message) {
        return ToolExecutionResult.builder().isError(true).result(message).resultText(message).build();
    }
    
    private static boolean hasRequiredPermissions(HarnessInvocation invocation, String expression) {
        if ("*".equals(expression)) {
            return true;
        }
        return java.util.Arrays.stream(expression.split(","))
                .map(String::trim).filter(permission -> !permission.isEmpty())
                .allMatch(invocation.permissions()::contains);
    }
}
