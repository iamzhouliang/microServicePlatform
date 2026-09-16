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

package com.microservice.platform.ai.core.workflow.config.node;

import com.microservice.platform.ai.core.enums.AiEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 模板转换节点配置
 * 使用 Jinja2/Freemarker 模板转换数据
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TemplateNodeConfig {

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 模板引擎类型
     */
    private TemplateEngine engine;

    /**
     * 模板内容
     * 支持 Jinja2/Freemarker 语法
     */
    private String template;

    /**
     * 输入变量列表
     */
    private List<TemplateVariable> variables;

    /**
     * 输出变量名
     */
    private String outputVariable;

    /**
     * 是否转义 HTML
     */
    private boolean escapeHtml;

    /**
     * 是否去除空白
     */
    private boolean trimWhitespace;

    /**
     * 严格模式（变量不存在时报错）
     */
    private boolean strictMode;

    /**
     * 模板变量定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemplateVariable {

        /**
         * 变量名（在模板中使用）
         */
        private String name;

        /**
         * 变量引用
         * 支持格式: {{nodeName.variableName}}
         */
        private String reference;

        /**
         * 默认值
         */
        private Object defaultValue;

        /**
         * 变量类型
         */
        private String type;
    }

    /**
     * 模板引擎枚举
     */
    @Getter
    @AllArgsConstructor
    public enum TemplateEngine implements AiEnum {

        /**
         * Jinja2 模板引擎
         * Python 风格的模板语法
         */
        JINJA2("JINJA2", "Jinja2"),

        /**
         * Freemarker 模板引擎
         * Java 生态常用
         */
        FREEMARKER("FREEMARKER", "Freemarker"),

        /**
         * 简单字符串替换
         * 仅支持 {{variable}} 格式
         */
        SIMPLE("SIMPLE", "简单替换");

        private final String code;
        private final String description;
    }

    /**
     * 验证结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {

        private boolean valid;
        private List<String> errors;

        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * 验证配置
     *
     * @return 验证结果
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // 模板引擎可以为空，默认使用 JINJA2

        // 验证模板内容
        if (template == null || template.trim().isEmpty()) {
            errors.add("模板内容不能为空");
        }

        // 验证输出变量名
        if (outputVariable == null || outputVariable.trim().isEmpty()) {
            errors.add("输出变量名不能为空");
        }

        // 验证变量定义
        if (variables != null) {
            Set<String> varNames = new HashSet<>();
            for (TemplateVariable var : variables) {
                if (var.getName() == null || var.getName().trim().isEmpty()) {
                    errors.add("变量名不能为空");
                } else if (!varNames.add(var.getName())) {
                    errors.add("变量名重复: " + var.getName());
                }
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 获取依赖的变量引用
     *
     * @return 依赖的变量引用列表
     */
    public List<String> getDependentVariables() {
        List<String> deps = new ArrayList<>();
        if (variables != null) {
            for (TemplateVariable var : variables) {
                if (var.getReference() != null) {
                    Matcher matcher = VARIABLE_PATTERN.matcher(var.getReference());
                    while (matcher.find()) {
                        deps.add(matcher.group(1).trim());
                    }
                }
            }
        }
        return deps;
    }

    /**
     * 获取依赖的节点ID
     *
     * @return 依赖的节点ID集合
     */
    public Set<String> getDependentNodeIds() {
        Set<String> nodeIds = new HashSet<>();
        for (String varPath : getDependentVariables()) {
            String[] parts = varPath.split("\\.", 2);
            if (parts.length > 0) {
                nodeIds.add(parts[0]);
            }
        }
        return nodeIds;
    }

    /**
     * 从模板中提取变量引用
     *
     * @return 模板中的变量引用列表
     */
    public List<String> extractTemplateVariables() {
        List<String> vars = new ArrayList<>();
        if (template != null) {
            // 提取 Jinja2 风格变量 {{ variable }}
            Matcher jinja2Matcher = Pattern.compile("\\{\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}\\}").matcher(template);
            while (jinja2Matcher.find()) {
                vars.add(jinja2Matcher.group(1));
            }

            // 提取 Freemarker 风格变量 ${variable}
            Matcher freemarkerMatcher = Pattern.compile("\\$\\{\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\}").matcher(template);
            while (freemarkerMatcher.find()) {
                vars.add(freemarkerMatcher.group(1));
            }
        }
        return vars;
    }
}
