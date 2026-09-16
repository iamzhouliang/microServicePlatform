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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.variable.VariableReference;
import com.microservice.platform.ai.core.workflow.variable.VariableResolver;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 节点执行器 抽象基类
 * 提供通用的模板方法和工具方法
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
public abstract class AbstractNodeExecutor implements NodeExecutor {

    /**
     * 变量引用模式：{{variableName}} 或 {{nodeId.outputKey}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * 共享的 ObjectMapper 实例，用于配置类解析
     */
    private static final ObjectMapper CONFIG_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);

    /**
     * 变量解析器（静态实例，线程安全）
     */
    private static final VariableResolver VARIABLE_RESOLVER = new VariableResolver();

    @Override
    public NodeExecutionResult execute(WorkflowNode node, ExecutionContext context) {
        long startTime = System.currentTimeMillis();
        String nodeId = node.getId();

        try {
            // 检查是否取消
            if (context.shouldStop()) {
                return NodeExecutionResult.failure("执行已取消");
            }

            // 设置当前节点
            context.setCurrentNodeId(nodeId);

            // 验证节点配置
            validate(node);

            // 执行节点逻辑
            NodeExecutionResult result = doExecute(node, context);

            // 设置执行耗时
            long duration = System.currentTimeMillis() - startTime;
            result.setDuration(duration);

            // 将输出存储到上下文变量中
            if (result.isSuccess() && result.getOutputs() != null) {
                // 存储到节点输出（统一使用节点 ID 作为 key）
                context.setNodeOutput(nodeId, result.getOutputs());

                for (Map.Entry<String, Object> entry : result.getOutputs().entrySet()) {
                    String key = nodeId + "." + entry.getKey();
                    context.setVariable(key, entry.getValue());
                }
            }

            log.debug("节点 {} 执行成功，耗时 {}ms", nodeId, duration);
            return result;

        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("节点 {} 执行失败: {}", nodeId, e.getMessage(), e);
            return NodeExecutionResult.failure(e.getMessage(), duration);
        }
    }

    /**
     * 执行节点逻辑（子类实现）
     *
     * @param node    工作流节点
     * @param context 执行上下文
     * @return 执行结果
     */
    protected abstract NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context);

    /**
     * 解析模板字符串中的变量引用
     * 支持格式：
     * - {{variableName}} - 直接引用变量
     * - {{nodeId.outputKey}} - 引用其他节点的输出
     *
     * @param template 模板字符串
     * @param context  执行上下文
     * @return 解析后的字符串
     */
    protected String resolveTemplate(String template, ExecutionContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String variableName = matcher.group(1).trim();
            Object value = context.getVariable(variableName, Object.class);

            if (value == null) {
                // 尝试解析 JSONPath 或嵌套引用
                value = resolveNestedVariable(variableName, context);
            }

            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 解析嵌套变量引用
     * @param context 执行上下文
     * @param variableName variableName 参数
     * @return 处理结果
     */
    private Object resolveNestedVariable(String variableName, ExecutionContext context) {
        // 尝试按点号分割
        String[] parts = variableName.split("\\.", 2);
        if (parts.length == 2) {
            String nodeId = parts[0];
            String outputKey = parts[1];
            return context.getVariable(nodeId + "." + outputKey, Object.class);
        }
        return null;
    }

    // ==================== 变量解析方法（使用 VariableResolver）====================

    /**
     * 使用 VariableResolver 解析字符串模板中的变量引用
     * 支持格式：
     * - {{nodeName.variableName}} - 引用节点输出
     * - {{nodeName.variableName.nested.path}} - 嵌套路径访问
     * - {{nodeName.variableName[0].property}} - 数组索引访问
     *
     * @param template 模板字符串
     * @param context  执行上下文
     * @return 解析后的字符串
     */
    protected String resolveVariables(String template, ExecutionContext context) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        return VARIABLE_RESOLVER.resolve(template, context.getAllNodeOutputs());
    }

    /**
     * 解析单个变量表达式并返回值
     *
     * @param expression 变量表达式 (e.g., "nodeName.variableName")
     * @param context    执行上下文
     * @return 解析后的值
     */
    protected Object resolveVariableValue(String expression, ExecutionContext context) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        return VARIABLE_RESOLVER.resolveExpression(expression, context.getAllNodeOutputs());
    }

    /**
     * 解析单个变量表达式并返回指定类型的值
     *
     * @param expression 变量表达式
     * @param context    执行上下文
     * @param type       期望的类型
     * @param <T>        返回类型
     * @return 解析后的值，如果类型不匹配返回 null
     */
    @SuppressWarnings("unchecked")
    protected <T> T resolveVariableValue(String expression, ExecutionContext context, Class<T> type) {
        Object value = resolveVariableValue(expression, context);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // 类型转换
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        return null;
    }

    /**
     * 解析变量引用（支持 {{}} 包裹或直接路径）
     *
     * @param reference 变量引用字符串
     * @param context   执行上下文
     * @return 解析后的值
     */
    protected Object resolveVariableReference(String reference, ExecutionContext context) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }

        String expression = reference.trim();
        // 移除 {{ }} 包裹
        if (expression.startsWith("{{") && expression.endsWith("}}")) {
            expression = expression.substring(2, expression.length() - 2).trim();
        }

        return resolveVariableValue(expression, context);
    }

    /**
     * 检查字符串是否包含变量引用
     *
     * @param text 要检查的字符串
     * @return 是否包含变量引用
     */
    protected boolean containsVariableReference(String text) {
        return VARIABLE_RESOLVER.containsVariableReference(text);
    }

    /**
     * 提取字符串中的所有变量引用
     *
     * @param text 要检查的字符串
     * @return 变量引用列表
     */
    protected List<VariableReference> extractVariableReferences(String text) {
        return VARIABLE_RESOLVER.extractVariableReferences(text);
    }

    /**
     * 创建输出 Map
     * @param key 参数键
     * @param value 参数值
     * @return 处理结果
     */
    protected Map<String, Object> createOutput(String key, Object value) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put(key, value);
        return outputs;
    }

    /**
     * 创建带 result 键的输出 Map
     * @param value 参数值
     * @return 处理结果
     */
    protected Map<String, Object> createResultOutput(Object value) {
        return createOutput("result", value);
    }

    /**
     * 获取必需的配置值
     *
     * @param <T> 元素类型
     * @param key 参数键
     * @param node 工作流节点
     * @param type 类型
     * @return 处理结果
     * @throws IllegalArgumentException 如果配置不存在
     */
    protected <T> T getRequiredConfigValue(WorkflowNode node, String key, Class<T> type) {
        T value = getConfigValue(node, key, type);
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("Required config '%s' is missing for node %s", key, node.getId()));
        }
        return value;
    }

    /**
     * 解析节点配置为强类型配置类
     * 将节点的 data Map 转换为指定的配置类实例
     *
     * @param node       工作流节点
     * @param configType 配置类类型
     * @param <T>        配置类泛型
     * @return 配置类实例
     * @throws IllegalArgumentException 如果解析失败
     */
    protected <T> T parseConfig(WorkflowNode node, Class<T> configType) {
        Map<String, Object> data = node.getData();
        if (data == null || data.isEmpty()) {
            try {
                return configType.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        String.format("Failed to create default config for node %s: %s",
                                node.getId(), e.getMessage()),
                        e);
            }
        }

        try {
            return CONFIG_MAPPER.convertValue(data, configType);
        } catch (Exception e) {
            log.warn("节点 {} 配置解析为 {} 失败: {}",
                    node.getId(), configType.getSimpleName(), e.getMessage());
            throw new IllegalArgumentException(
                    String.format("节点 %s 配置解析失败: %s",
                            node.getId(), e.getMessage()),
                    e);
        }
    }

    /**
     * 解析节点配置为强类型配置类（带默认值）
     *
     * @param node         工作流节点
     * @param configType   配置类类型
     * @param defaultValue 解析失败时的默认值
     * @param <T>          配置类泛型
     * @return 配置类实例，解析失败返回默认值
     */
    protected <T> T parseConfigOrDefault(WorkflowNode node, Class<T> configType, T defaultValue) {
        try {
            return parseConfig(node, configType);
        } catch (Exception e) {
            log.debug("节点 {} 使用默认配置: {}", node.getId(), e.getMessage());
            return defaultValue;
        }
    }
}
