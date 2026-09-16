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

import java.util.Objects;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.Accessors;

/**
 * Toolset 的稳定描述信息。
 *
 * <p>本类型只表达能力分组和启用条件，Tool Schema 与执行器仍由 LangChain4j 提供。</p>
 */
@Getter
@ToString
@EqualsAndHashCode
@Accessors(fluent = true)
public final class ToolsetDescriptor {
    
    private final String id;
    private final String version;
    private final ToolsetSource source;
    private final String description;
    private final Set<String> routes;
    private final Set<String> taskTypes;
    private final Set<String> environments;
    private final Set<String> dependencies;
    private final Set<String> requiredPermissions;
    private final boolean dynamic;
    
    /**
     * 创建不可变 Toolset 描述。
     *
     * @param id Toolset 稳定标识
     * @param version Toolset 修订版本
     * @param source Toolset 来源
     * @param description 面向模型路由的低敏描述
     * @param routes 允许的内部路由
     * @param taskTypes 允许的任务类型
     * @param environments 允许的运行环境
     * @param dependencies 必须可用的外部依赖
     * @param requiredPermissions Toolset 级所需权限
     * @param dynamic 是否为动态 Toolset
     */
    public ToolsetDescriptor(String id, String version, ToolsetSource source, String description,
                             Set<String> routes, Set<String> taskTypes, Set<String> environments,
                             Set<String> dependencies, Set<String> requiredPermissions, boolean dynamic) {
        this.id = requireText(id, "Toolset 标识");
        this.version = requireText(version, "Toolset 版本");
        this.source = Objects.requireNonNull(source, "Toolset 来源不能为空");
        this.description = requireText(description, "Toolset 描述");
        this.routes = immutable(routes, "路由集合");
        this.taskTypes = immutable(taskTypes, "任务类型集合");
        this.environments = immutable(environments, "环境集合");
        this.dependencies = immutable(dependencies, "依赖集合");
        this.requiredPermissions = immutable(requiredPermissions, "权限集合");
        this.dynamic = dynamic;
    }
    
    private static Set<String> immutable(Set<String> values, String field) {
        Objects.requireNonNull(values, field + "不能为空");
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(field + "不能包含空值");
        }
        return Set.copyOf(values);
    }
    
    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
