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

/** 默认按路由、任务、环境、依赖和权限顺序裁决 Toolset。 */
public final class DefaultToolsetAvailabilityPolicy implements ToolsetAvailabilityPolicy {
    
    @Override
    public ToolsetDecision evaluate(ToolsetDescriptor descriptor, ToolsetSelectionContext context) {
        if (!descriptor.routes().isEmpty() && !descriptor.routes().contains(context.route())) {
            return ToolsetDecision.hidden(descriptor.id(), "ROUTE_MISMATCH");
        }
        if (!descriptor.taskTypes().isEmpty() && !descriptor.taskTypes().contains(context.taskType())) {
            return ToolsetDecision.hidden(descriptor.id(), "TASK_MISMATCH");
        }
        if (!descriptor.environments().isEmpty() && !descriptor.environments().contains(context.environment())) {
            return ToolsetDecision.hidden(descriptor.id(), "ENVIRONMENT_MISMATCH");
        }
        if (!context.availableDependencies().containsAll(descriptor.dependencies())) {
            return ToolsetDecision.hidden(descriptor.id(), "DEPENDENCY_UNAVAILABLE");
        }
        if (!hasPermissions(context.permissions(), descriptor.requiredPermissions())) {
            return ToolsetDecision.hidden(descriptor.id(), "TOOLSET_PERMISSION_DENIED");
        }
        return ToolsetDecision.available(descriptor.id());
    }
    
    static boolean hasPermissionExpression(java.util.Set<String> permissions, String expression) {
        if ("*".equals(expression)) {
            return true;
        }
        return java.util.Arrays.stream(expression.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .allMatch(permissions::contains);
    }
    
    private static boolean hasPermissions(java.util.Set<String> permissions, java.util.Set<String> required) {
        return required.isEmpty() || permissions.containsAll(required);
    }
}
