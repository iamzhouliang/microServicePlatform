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

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.TemplateNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.TemplateNodeConfig.TemplateEngine;
import com.microservice.platform.ai.core.workflow.config.node.TemplateNodeConfig.TemplateVariable;
import com.microservice.platform.ai.core.workflow.config.node.TemplateNodeConfig.ValidationResult;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import freemarker.core.TemplateClassResolver;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板转换节点执行器
 * 使用 Jinja2/Freemarker 模板语法转换数据
 * 功能特性:
 * - 支持 Jinja2 风格模板 ({{ variable }})
 * - 支持 Freemarker 模板 (${variable})
 * - 支持简单字符串替换
 * - 支持变量引用和默认值
 *
 * @author xJh
 * @since 2026/01/08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TemplateNodeExecutor extends AbstractNodeExecutor {

    /**
     * Jinja2 风格变量模式: {{ variable }}
     */
    private static final Pattern JINJA2_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_.]*)\\s*\\}\\}");

    /**
     * Freemarker 配置
     */
    private static final Configuration FREEMARKER_CONFIG;

    static {
        FREEMARKER_CONFIG = new Configuration(Configuration.VERSION_2_3_32);
        FREEMARKER_CONFIG.setDefaultEncoding("UTF-8");
        FREEMARKER_CONFIG.setLogTemplateExceptions(false);
        // 安全：模板由工作流设计者编写，禁止 ?new 实例化任意类，防止 Freemarker SSTI/RCE
        // （如 freemarker.template.utility.Execute / ObjectConstructor 等 gadget）
        FREEMARKER_CONFIG.setNewBuiltinClassResolver(TemplateClassResolver.ALLOWS_NOTHING_RESOLVER);
        FREEMARKER_CONFIG.setAPIBuiltinEnabled(false);
    }

    /**
     * 模板渲染器（用于测试注入）
     */
    @Setter
    private TemplateRenderer templateRenderer;

    /**
     * 模板渲染器接口
     * @return 处理结果
     */
    @FunctionalInterface
    public interface TemplateRenderer {

        String render(String template, Map<String, Object> variables, TemplateEngine engine) throws Exception;
    }

    @Override
    public NodeType getType() {
        return NodeType.TEMPLATE;
    }

    @Override
    public void validate(WorkflowNode node) {
        TemplateNodeConfig config = parseConfig(node, TemplateNodeConfig.class);

        if (config == null) {
            throw new IllegalArgumentException("模板节点缺少强类型配置");
        }

        // 使用强类型配置验证
        ValidationResult validationResult = config.validate();
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(
                    "模板节点配置无效: " + String.join(", ", validationResult.getErrors()));
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 TEMPLATE 节点: {}", node.getId());

        // 解析强类型配置
        TemplateNodeConfig config = parseConfig(node, TemplateNodeConfig.class);

        // 获取模板内容
        String template = config.getTemplate();
        if (template == null || template.trim().isEmpty()) {
            return NodeExecutionResult.failure("模板内容为空");
        }

        // 获取模板引擎
        TemplateEngine engine = config.getEngine();
        if (engine == null) {
            engine = TemplateEngine.JINJA2;
        }

        try {
            // 构建变量上下文
            Map<String, Object> variables = buildVariableContext(config, context);

            // 渲染模板
            String result;
            if (templateRenderer != null) {
                // 使用注入的渲染器（测试用）
                result = templateRenderer.render(template, variables, engine);
            } else {
                // 使用默认渲染器
                result = renderTemplate(template, variables, engine, config.isStrictMode());
            }

            // 处理输出选项
            if (config.isTrimWhitespace()) {
                result = result.trim();
            }

            if (config.isEscapeHtml()) {
                result = escapeHtml(result);
            }

            // 构建输出
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);

            // 设置配置的输出变量
            String outputVariable = config.getOutputVariable();
            if (outputVariable != null && !outputVariable.isEmpty()) {
                outputs.put(outputVariable, result);
            }

            log.debug("TEMPLATE 节点 {} 执行成功", node.getId());
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("模板节点 {} 渲染失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("模板渲染失败: " + e.getMessage());
        }
    }

    /**
     * 构建变量上下文
     * @param config 节点配置
     * @param context 执行上下文
     * @return 处理结果
     * @throws Exception 处理失败时抛出
     */
    private Map<String, Object> buildVariableContext(TemplateNodeConfig config, ExecutionContext context) {
        Map<String, Object> variables = new HashMap<>();

        // 处理配置的变量映射
        List<TemplateVariable> templateVars = config.getVariables();
        if (templateVars != null && !templateVars.isEmpty()) {
            for (TemplateVariable var : templateVars) {
                String name = var.getName();
                String reference = var.getReference();

                if (name != null && reference != null && !reference.isEmpty()) {
                    // 解析变量引用
                    String varPath = reference.trim();
                    if (varPath.startsWith("{{") && varPath.endsWith("}}")) {
                        varPath = varPath.substring(2, varPath.length() - 2).trim();
                    }
                    Object value = context.getVariable(varPath, Object.class);

                    // 使用默认值
                    if (value == null && var.getDefaultValue() != null) {
                        value = var.getDefaultValue();
                    }

                    variables.put(name, value);
                }
            }
        } else {
            // 如果没有配置变量映射，使用所有上下文变量
            variables.putAll(context.getAllVariables());
        }

        return variables;
    }

    /**
     * 渲染模板
     * @param strictMode strictMode 参数
     * @param template 模板内容
     * @param variables 变量集合
     * @param engine engine 参数
     * @return 处理结果
     * @throws Exception 处理失败时抛出
     */
    private String renderTemplate(String template, Map<String, Object> variables,
                                  TemplateEngine engine, boolean strictMode) throws Exception {
        return switch (engine) {
            case JINJA2 -> renderJinja2Template(template, variables, strictMode);
            case FREEMARKER -> renderFreemarkerTemplate(template, variables);
            case SIMPLE -> renderSimpleTemplate(template, variables, strictMode);
        };
    }

    /**
     * 渲染 Jinja2 风格模板
     * 使用简单的正则替换实现基本的 Jinja2 语法
     * @param strictMode strictMode 参数
     * @param template 模板内容
     * @param variables 变量集合
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private String renderJinja2Template(String template, Map<String, Object> variables, boolean strictMode) {
        Matcher matcher = JINJA2_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String varName = matcher.group(1).trim();
            Object value = resolveVariableValue(varName, variables);

            if (value == null && strictMode) {
                throw new IllegalArgumentException("变量不存在: " + varName);
            }

            String replacement = value != null ? String.valueOf(value) : "";
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 渲染 Freemarker 模板
     * @param template 模板内容
     * @param variables 变量集合
     * @return 处理结果
     * @throws IOException 处理失败时抛出
     * @throws TemplateException 处理失败时抛出
     */
    private String renderFreemarkerTemplate(String template, Map<String, Object> variables) throws IOException, TemplateException {
        Template freemarkerTemplate = new Template("template", new StringReader(template), FREEMARKER_CONFIG);
        StringWriter writer = new StringWriter();
        freemarkerTemplate.process(variables, writer);
        return writer.toString();
    }

    /**
     * 渲染简单模板（仅支持 {{variable}} 替换）
     * @param strictMode strictMode 参数
     * @param template 模板内容
     * @param variables 变量集合
     * @return 处理结果
     */
    private String renderSimpleTemplate(String template, Map<String, Object> variables, boolean strictMode) {
        return renderJinja2Template(template, variables, strictMode);
    }

    /**
     * 解析变量值（支持嵌套路径）
     * @param variables 变量集合
     * @param varPath varPath 参数
     * @return 处理结果
     */
    private Object resolveVariableValue(String varPath, Map<String, Object> variables) {
        // 直接查找
        if (variables.containsKey(varPath)) {
            return variables.get(varPath);
        }

        // 尝试解析嵌套路径 (e.g., "user.name")
        String[] parts = varPath.split("\\.");
        Object current = variables.get(parts[0]);

        for (int i = 1; i < parts.length && current != null; i++) {
            if (current instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) current;
                current = map.get(parts[i]);
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * HTML 转义
     * @param text 文本内容
     * @return 处理结果
     */
    private String escapeHtml(String text) {
        if (text == null) {
            return null;
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
