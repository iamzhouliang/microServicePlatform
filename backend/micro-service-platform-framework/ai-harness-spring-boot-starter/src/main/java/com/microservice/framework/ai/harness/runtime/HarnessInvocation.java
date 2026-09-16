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

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 由认证层创建并随 LangChain4j 调用传递的不可变业务身份。
 *
 * <p>该对象不能从用户请求 DTO 反序列化，Tool 只能通过 {@link InvocationParameters} 读取。</p>
 *
 * @param tenantId      租户标识
 * @param userId        用户标识
 * @param conversationId 会话标识
 * @param operationId   操作标识
 * @param turnId        当前 HTTP 用户轮标识
 * @param permissions   当前权限快照
 * @param resumeOperation 是否恢复已持久化的待处理 Operation
 * @author xJh
 * @since 2026-07-18
 */
public record HarnessInvocation(String tenantId, String userId, String conversationId, String operationId,
        String turnId, Set<String> permissions, boolean resumeOperation) {

    public static final String PARAMETER_KEY = HarnessInvocation.class.getName();

    public HarnessInvocation {
        tenantId = requireText(tenantId, "租户标识");
        userId = requireText(userId, "用户标识");
        conversationId = requireText(conversationId, "会话标识");
        operationId = requireText(operationId, "操作标识");
        turnId = requireText(turnId, "用户轮标识");
        permissions = Set.copyOf(Objects.requireNonNull(permissions, "权限集合不能为空"));
    }

    /** 控制面和兼容调用传入的 operationId 均表示明确恢复已有 Operation。 */
    public HarnessInvocation(String tenantId, String userId, String conversationId, String operationId,
                             String turnId, Set<String> permissions) {
        this(tenantId, userId, conversationId, operationId, turnId, permissions, true);
    }

    /**
     * 为当前 LangChain4j Tool Call 生成稳定 Operation 标识。
     *
     * <p>新用户轮按 Tool Call 派生，避免同轮多个写 Tool 共用一个 Operation；恢复轮始终沿用原标识。</p>
     */
    public HarnessInvocation forToolCall(String toolCallId) {
        if (resumeOperation) {
            return this;
        }
        String call = requireText(toolCallId, "Tool Call 标识");
        String source = tenantId + ":" + conversationId + ":" + turnId + ":" + call;
        String derivedOperationId = UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
        return new HarnessInvocation(tenantId, userId, conversationId, derivedOperationId, turnId,
                permissions, false);
    }

    /** 从官方调用上下文读取可信身份。 */
    public static Optional<HarnessInvocation> from(InvocationContext context) {
        if (context == null || context.invocationParameters() == null) {
            return Optional.empty();
        }
        return from(context.invocationParameters());
    }

    /** Tool 方法参数可直接接收官方 InvocationParameters，并从中读取可信身份。 */
    public static Optional<HarnessInvocation> from(InvocationParameters parameters) {
        if (parameters == null) {
            return Optional.empty();
        }
        Object value = parameters.get(PARAMETER_KEY);
        return value instanceof HarnessInvocation invocation ? Optional.of(invocation) : Optional.empty();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
