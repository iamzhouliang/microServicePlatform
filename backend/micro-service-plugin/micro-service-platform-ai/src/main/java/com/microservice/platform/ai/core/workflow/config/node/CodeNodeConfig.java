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
 * 代码节点配置
 * 执行 Python/JavaScript 代码
 * 输出限制:
 * - 字符串最大 200KB
 * - 数组最大 100 元素
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CodeNodeConfig {

    // ==================== 输出限制常量 ====================

    /**
     * 字符串最大长度（200KB）
     */
    public static final int MAX_STRING_LENGTH = 200 * 1024;

    /**
     * 数组最大元素数量
     */
    public static final int MAX_ARRAY_SIZE = 100;

    /**
     * 默认超时时间（毫秒）
     */
    public static final long DEFAULT_TIMEOUT = 30000L;

    /**
     * 默认最大内存（MB）
     */
    public static final int DEFAULT_MAX_MEMORY = 128;

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 编程语言
     */
    private CodeLanguage language;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 输入变量列表
     */
    private List<InputVariable> inputs;

    /**
     * 输出变量列表
     */
    private List<OutputVariable> outputs;

    /**
     * 执行超时时间（毫秒）
     */
    private Long timeout;

    /**
     * 最大内存限制（MB）
     */
    private Integer maxMemory;

    /**
     * 是否启用沙箱模式
     */
    private boolean sandboxEnabled;

    /**
     * 输出变量名（存储执行结果）
     */
    private String outputVariable;

    /**
     * 编程语言枚举
     */
    @Getter
    @AllArgsConstructor
    public enum CodeLanguage implements AiEnum {

        PYTHON("PYTHON", "Python 3"),
        JAVASCRIPT("JAVASCRIPT", "JavaScript (Node.js)");

        private final String code;
        private final String description;
    }

    /**
     * 输入变量定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InputVariable {

        /**
         * 变量名（在代码中使用）
         */
        private String name;

        /**
         * 源变量引用
         * 支持格式: {{nodeName.variableName}}
         */
        private String sourceVariable;

        /**
         * 变量类型
         */
        private String type;
    }

    /**
     * 输出变量定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OutputVariable {

        /**
         * 变量名
         */
        private String name;

        /**
         * 变量类型
         */
        private String type;

        /**
         * 变量描述
         */
        private String description;
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

        // 验证语言
        if (language == null) {
            errors.add("编程语言不能为空");
        }

        // 验证代码
        if (code == null || code.trim().isEmpty()) {
            errors.add("代码内容不能为空");
        }

        // 验证输入变量
        if (inputs != null) {
            Set<String> inputNames = new HashSet<>();
            for (InputVariable input : inputs) {
                if (input.getName() == null || input.getName().trim().isEmpty()) {
                    errors.add("输入变量名不能为空");
                } else if (!inputNames.add(input.getName())) {
                    errors.add("输入变量名重复: " + input.getName());
                }
                if (input.getSourceVariable() == null || input.getSourceVariable().trim().isEmpty()) {
                    errors.add("输入变量 " + input.getName() + " 的源变量引用不能为空");
                }
            }
        }

        // 验证输出变量
        if (outputs != null) {
            Set<String> outputNames = new HashSet<>();
            for (OutputVariable output : outputs) {
                if (output.getName() == null || output.getName().trim().isEmpty()) {
                    errors.add("输出变量名不能为空");
                } else if (!outputNames.add(output.getName())) {
                    errors.add("输出变量名重复: " + output.getName());
                }
            }
        }

        // 验证超时时间
        if (timeout != null && timeout <= 0) {
            errors.add("超时时间必须大于0");
        }

        // 验证内存限制
        if (maxMemory != null && maxMemory <= 0) {
            errors.add("内存限制必须大于0");
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 获取依赖的变量引用
     *
     * @return 依赖的变量引用列表
     */
    public List<String> getDependentVariables() {
        List<String> variables = new ArrayList<>();
        if (inputs != null) {
            for (InputVariable input : inputs) {
                if (input.getSourceVariable() != null) {
                    Matcher matcher = VARIABLE_PATTERN.matcher(input.getSourceVariable());
                    while (matcher.find()) {
                        variables.add(matcher.group(1).trim());
                    }
                }
            }
        }
        return variables;
    }

    /**
     * 验证输出值是否符合限制
     *
     * @param value 输出值
     * @return 验证结果
     */
    public static ValidationResult validateOutput(Object value) {
        List<String> errors = new ArrayList<>();

        if (value instanceof String str) {
            if (str.length() > MAX_STRING_LENGTH) {
                errors.add("字符串输出超过最大长度限制 " + MAX_STRING_LENGTH + " 字节，当前长度: " + str.length());
            }
        } else if (value instanceof List<?> list) {
            if (list.size() > MAX_ARRAY_SIZE) {
                errors.add("数组输出超过最大元素数量限制 " + MAX_ARRAY_SIZE + "，当前数量: " + list.size());
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 获取有效的超时时间
     *
     * @return 超时时间（毫秒）
     */
    public long getEffectiveTimeout() {
        return timeout != null && timeout > 0 ? timeout : DEFAULT_TIMEOUT;
    }

    /**
     * 获取有效的内存限制
     *
     * @return 内存限制（MB）
     */
    public int getEffectiveMaxMemory() {
        return maxMemory != null && maxMemory > 0 ? maxMemory : DEFAULT_MAX_MEMORY;
    }
}
