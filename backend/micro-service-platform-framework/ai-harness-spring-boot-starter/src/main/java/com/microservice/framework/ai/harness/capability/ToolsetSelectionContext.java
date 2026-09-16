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

package com.microservice.framework.ai.harness.capability;

import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * 服务端构造的 Toolset 选择上下文。
 */
@Getter
@ToString
@EqualsAndHashCode
@Accessors(fluent = true)
public final class ToolsetSelectionContext {
    
    public static final String PARAMETER_KEY = ToolsetSelectionContext.class.getName();
    
    private final String tenantId;
    private final String userId;
    private final String conversationId;
    private final String turnId;
    private final String route;
    private final String taskType;
    private final String environment;
    private final Set<String> permissions;
    private final Set<String> requestedToolsets;
    private final Set<String> availableDependencies;
    
    /**
     * 创建可信 Toolset 选择上下文。
     *
     * @param tenantId 可信租户标识
     * @param userId 可信用户标识
     * @param conversationId 会话标识
     * @param turnId 用户轮标识
     * @param route 内部路由
     * @param taskType 任务类型
     * @param environment 当前运行环境
     * @param permissions 当前可信权限
     * @param requestedToolsets 显式请求的 Toolset
     * @param availableDependencies 当前可用依赖
     */
    public ToolsetSelectionContext(String tenantId, String userId, String conversationId, String turnId,
                                   String route, String taskType, String environment, Set<String> permissions,
                                   Set<String> requestedToolsets, Set<String> availableDependencies) {
        this.tenantId = requireText(tenantId, "租户标识");
        this.userId = requireText(userId, "用户标识");
        this.conversationId = requireText(conversationId, "会话标识");
        this.turnId = requireText(turnId, "用户轮标识");
        this.route = requireText(route, "路由类型");
        this.taskType = requireText(taskType, "任务类型");
        this.environment = requireText(environment, "运行环境");
        this.permissions = Set.copyOf(Objects.requireNonNull(permissions, "权限集合不能为空"));
        this.requestedToolsets = Set.copyOf(Objects.requireNonNull(requestedToolsets, "请求 Toolset 集合不能为空"));
        this.availableDependencies = Set.copyOf(
                Objects.requireNonNull(availableDependencies, "可用依赖集合不能为空"));
    }
    
    /**
     * 创建仅替换显式 Toolset 范围的新上下文。
     *
     * @param toolsetIds 新的 Toolset 范围
     * @return 不可变选择上下文
     */
    public ToolsetSelectionContext withRequestedToolsets(Set<String> toolsetIds) {
        return new ToolsetSelectionContext(tenantId, userId, conversationId, turnId, route, taskType, environment,
                permissions, toolsetIds, availableDependencies);
    }
    
    /**
     * 从 LangChain4j 官方调用上下文读取服务端构造的能力选择上下文。
     *
     * @param context LangChain4j 调用上下文
     * @return 可信选择上下文
     */
    public static Optional<ToolsetSelectionContext> from(InvocationContext context) {
        if (context == null) {
            return Optional.empty();
        }
        return from(context.invocationParameters());
    }
    
    /**
     * 从 LangChain4j 官方调用参数读取服务端构造的能力选择上下文。
     *
     * @param parameters LangChain4j 调用参数
     * @return 可信选择上下文
     */
    public static Optional<ToolsetSelectionContext> from(InvocationParameters parameters) {
        if (parameters == null) {
            return Optional.empty();
        }
        Object value = parameters.get(PARAMETER_KEY);
        return value instanceof ToolsetSelectionContext selection ? Optional.of(selection) : Optional.empty();
    }
    
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
