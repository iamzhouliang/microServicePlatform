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

package com.microservice.platform.ai.core.workflow.agent;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;

import java.util.Map;

/**
 * 节点执行器 接口
 * 每个节点类型实现此接口，负责执行特定类型节点的业务逻辑。
 * 节点执行器 将工作流节点配置转换为可执行的操作。
 *
 * @author xJh
 * @since 2026/01/07
 */
public interface NodeExecutor {

    /**
     * 获取节点类型
     *
     * @return 节点类型枚举
     */
    NodeType getType();

    /**
     * 执行节点
     *
     * @param node    工作流节点定义
     * @param context 执行上下文
     * @return 节点执行结果
     */
    NodeExecutionResult execute(WorkflowNode node, ExecutionContext context);

    /**
     * 验证节点配置
     *
     * @param node 工作流节点定义
     * @throws IllegalArgumentException 如果配置无效
     */
    default void validate(WorkflowNode node) {
        // 默认不做验证，子类可覆盖
    }

    /**
     * 获取节点配置中的指定字段值
     *
     * @param node 工作流节点
     * @param key  配置字段名
     * @param type 值类型
     * @param <T>  泛型类型
     * @return 配置值，如果不存在返回 null
     */
    @SuppressWarnings("unchecked")
    default <T> T getConfigValue(WorkflowNode node, String key, Class<T> type) {
        Map<String, Object> data = node.getData();
        if (data == null) {
            return null;
        }
        Object value = data.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // 处理数字类型转换
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        if (type == Boolean.class && value instanceof Boolean) {
            return (T) value;
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        return (T) value;
    }

    /**
     * 获取节点配置中的指定字段值，带默认值
     *
     * @param node         工作流节点
     * @param key          配置字段名
     * @param type         值类型
     * @param defaultValue 默认值
     * @param <T>          泛型类型
     * @return 配置值，如果不存在返回默认值
     */
    default <T> T getConfigValue(WorkflowNode node, String key, Class<T> type, T defaultValue) {
        T value = getConfigValue(node, key, type);
        return value != null ? value : defaultValue;
    }
}
