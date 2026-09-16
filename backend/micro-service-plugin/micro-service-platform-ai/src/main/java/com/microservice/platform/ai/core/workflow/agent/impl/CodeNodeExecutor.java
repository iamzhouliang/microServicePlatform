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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig.CodeLanguage;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig.InputVariable;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig.OutputVariable;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig.ValidationResult;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 代码节点执行器
 * 执行 Python/JavaScript 代码片段进行数据处理
 * 使用 GraalVM Polyglot 引擎执行 JavaScript 代码
 * 功能特性:
 * - 使用 CodeNodeConfig 强类型配置
 * - 支持输入/输出变量定义
 * - 输出限制验证（字符串最大 200KB，数组最大 100 元素）
 * - 超时控制
 * - 沙箱执行环境
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CodeNodeExecutor extends AbstractNodeExecutor {

    private final ObjectMapper objectMapper;

    /**
     * 代码执行器（用于测试注入）
     */
    @Setter
    private CodeExecutor codeExecutor;

    /**
     * 代码执行器接口
     */
    @FunctionalInterface
    public interface CodeExecutor {

        Object execute(String code, Map<String, Object> variables, CodeLanguage language, long timeout) throws Exception;
    }

    @Override
    public NodeType getType() {
        return NodeType.CODE;
    }

    @Override
    public void validate(WorkflowNode node) {
        CodeNodeConfig config = parseConfig(node, CodeNodeConfig.class);

        // 使用强类型配置验证
        ValidationResult validationResult = config.validate();
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(
                    "Code node configuration invalid: " + String.join(", ", validationResult.getErrors()));
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 CODE 节点: {}", node.getId());

        // 解析强类型配置
        CodeNodeConfig config = parseConfig(node, CodeNodeConfig.class);

        // 获取代码配置
        String code = config.getCode();
        if (code == null || code.trim().isEmpty()) {
            return NodeExecutionResult.failure("代码内容为空");
        }

        CodeLanguage language = config.getLanguage();
        if (language == null) {
            language = CodeLanguage.JAVASCRIPT;
        }

        // 目前只支持 JavaScript
        if (language != CodeLanguage.JAVASCRIPT) {
            return NodeExecutionResult.failure(
                    "不支持的代码语言: " + language.getCode() + "，当前仅支持 JavaScript。");
        }

        long timeout = config.getEffectiveTimeout();

        try {
            // 构建输入变量
            Map<String, Object> variables = buildInputVariables(config, context);

            // 执行代码
            Object result;
            if (codeExecutor != null) {
                // 使用注入的执行器（测试用）
                result = codeExecutor.execute(code, variables, language, timeout);
            } else {
                // 使用默认执行器
                result = executeWithTimeout(code, variables, timeout);
            }

            // 验证所有输出变量的限制
            Map<String, Object> outputs = buildOutputs(config, result);
            String validationError = validateAllOutputs(outputs);
            if (validationError != null) {
                return NodeExecutionResult.failure(validationError);
            }

            log.debug("CODE 节点 {} 执行成功", node.getId());
            return NodeExecutionResult.success(outputs);

        } catch (TimeoutException e) {
            log.error("代码节点 {} 执行超时: {}ms", node.getId(), timeout);
            return NodeExecutionResult.failure("代码执行超时，耗时 " + timeout + "ms");
        } catch (PolyglotException e) {
            log.error("代码节点 {} 执行错误: {}", node.getId(), e.getMessage());
            return NodeExecutionResult.failure("代码执行错误: " + e.getMessage());
        } catch (Exception e) {
            log.error("代码节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("代码执行失败: " + e.getMessage());
        }
    }

    /**
     * 构建输入变量
     * @param config 节点配置
     * @param context 执行上下文
     * @return 处理结果
     */
    private Map<String, Object> buildInputVariables(CodeNodeConfig config, ExecutionContext context) {
        Map<String, Object> variables = new HashMap<>();

        // 处理配置的输入变量映射
        List<InputVariable> inputs = config.getInputs();
        if (inputs != null && !inputs.isEmpty()) {
            for (InputVariable input : inputs) {
                String name = input.getName();
                String sourceVariable = input.getSourceVariable();

                if (name != null && sourceVariable != null && !sourceVariable.isEmpty()) {
                    // 解析变量引用
                    String varPath = sourceVariable.trim();
                    if (varPath.startsWith("{{") && varPath.endsWith("}}")) {
                        varPath = varPath.substring(2, varPath.length() - 2).trim();
                    }
                    Object value = context.getVariable(varPath, Object.class);
                    variables.put(name, value);
                }
            }
        } else {
            // 如果没有配置输入变量，使用所有上下文变量
            variables.putAll(context.getAllVariables());
        }

        return variables;
    }

    /**
     * 构建输出
     * @param config 节点配置
     * @param result 处理结果
     * @return 处理结果
     */
    private Map<String, Object> buildOutputs(CodeNodeConfig config, Object result) {
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", result);

        // 如果配置了输出变量，尝试从结果中提取
        List<OutputVariable> outputVars = config.getOutputs();
        if (outputVars != null && !outputVars.isEmpty() && result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> resultMap = (Map<String, Object>) result;
            for (OutputVariable outputVar : outputVars) {
                String name = outputVar.getName();
                if (name != null && resultMap.containsKey(name)) {
                    outputs.put(name, resultMap.get(name));
                }
            }
        }

        // 设置配置的输出变量名
        String outputVariable = config.getOutputVariable();
        if (outputVariable != null && !outputVariable.isEmpty()) {
            outputs.put(outputVariable, result);
        }

        return outputs;
    }

    /**
     * 验证所有输出是否符合限制
     * @param outputs 输出数据
     * @return 处理结果
     */
    private String validateAllOutputs(Map<String, Object> outputs) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
            ValidationResult validation = CodeNodeConfig.validateOutput(entry.getValue());
            if (!validation.isValid()) {
                errors.addAll(validation.getErrors().stream()
                        .map(err -> entry.getKey() + ": " + err)
                        .toList());
            }
        }

        return errors.isEmpty() ? null : String.join("; ", errors);
    }

    /**
     * 带超时的代码执行
     * @param code 编码
     * @param timeout 超时时间
     * @param variables 变量集合
     * @return 处理结果
     * @throws ExecutionException 处理失败时抛出
     * @throws InterruptedException 处理失败时抛出
     * @throws TimeoutException 处理失败时抛出
     */
    private Object executeWithTimeout(String code, Map<String, Object> variables, long timeout) throws TimeoutException, ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<Object> task = () -> executeJavaScript(code, variables);
            Future<Object> future = executor.submit(task);
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * 执行 JavaScript 代码
     * @param code 编码
     * @param variables 变量集合
     * @return 处理结果
     */
    private Object executeJavaScript(String code, Map<String, Object> variables) {
        try (
                Context jsContext = Context.newBuilder("js")
                        .allowAllAccess(false)
                        .option("engine.WarnInterpreterOnly", "false")
                        .build()) {

            // 将变量注入到 JavaScript 上下文
            Value bindings = jsContext.getBindings("js");
            for (Map.Entry<String, Object> entry : variables.entrySet()) {
                String key = sanitizeVariableName(entry.getKey());
                bindings.putMember(key, entry.getValue());
            }

            // 包装代码，确保有返回值
            String wrappedCode = wrapCode(code);

            // 执行代码
            Value result = jsContext.eval("js", wrappedCode);

            // 转换结果
            return convertValue(result);
        }
    }

    /**
     * 清理变量名（移除不合法字符）
     * @param name 名称
     * @return 处理结果
     */
    private String sanitizeVariableName(String name) {
        if (name == null) {
            return "var";
        }
        return name.replace(".", "_").replace("-", "_").replace(" ", "_");
    }

    /**
     * 包装代码以确保有返回值
     * @param code 编码
     * @return 处理结果
     */
    private String wrapCode(String code) {
        String trimmed = code.trim();

        // 如果代码已经包含 return 语句，包装成 IIFE
        if (trimmed.contains("return ")) {
            return "(function() { " + code + " })()";
        }

        // 如果是括号包裹的对象字面量 ({...})，直接返回表达式
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed;
        }

        // 如果是简单表达式（无分号），直接返回
        if (!trimmed.contains(";")) {
            return trimmed;
        }

        // 多语句代码，包装成 IIFE 并返回最后一个表达式
        // 尝试将最后一个语句作为返回值
        int lastSemicolon = trimmed.lastIndexOf(';');
        if (lastSemicolon > 0 && lastSemicolon < trimmed.length() - 1) {
            String beforeLast = trimmed.substring(0, lastSemicolon + 1);
            String lastExpr = trimmed.substring(lastSemicolon + 1).trim();
            if (!lastExpr.isEmpty()) {
                return "(function() { " + beforeLast + " return " + lastExpr + "; })()";
            }
        }

        // 其他情况包装成 IIFE
        return "(function() { " + code + " })()";
    }

    /**
     * 转换 GraalVM Value 为 Java 对象
     * @param value 参数值
     * @return 处理结果
     */
    private Object convertValue(Value value) {
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isBoolean()) {
            return value.asBoolean();
        }
        if (value.isNumber()) {
            if (value.fitsInLong()) {
                return value.asLong();
            }
            return value.asDouble();
        }
        if (value.isString()) {
            return value.asString();
        }
        if (value.hasArrayElements()) {
            int size = (int) value.getArraySize();
            List<Object> list = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                list.add(convertValue(value.getArrayElement(i)));
            }
            return list;
        }
        if (value.hasMembers()) {
            Map<String, Object> map = new HashMap<>();
            for (String key : value.getMemberKeys()) {
                map.put(key, convertValue(value.getMember(key)));
            }
            return map;
        }
        return value.toString();
    }
}
