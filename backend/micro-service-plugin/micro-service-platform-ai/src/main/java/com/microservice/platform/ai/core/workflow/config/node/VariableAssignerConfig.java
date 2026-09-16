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
import com.microservice.platform.ai.core.workflow.enums.WorkflowValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量赋值节点配置
 * 设置和转换变量
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VariableAssignerConfig {

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 变量赋值列表
     */
    private List<Assignment> assignments;

    /**
     * 获取所有依赖的变量引用
     *
     * @return 变量引用列表
     */
    public List<String> getDependentVariables() {
        List<String> variables = new ArrayList<>();
        if (assignments == null) {
            return variables;
        }

        for (Assignment assignment : assignments) {
            if (assignment.getType() == AssignmentType.VARIABLE || assignment.getType() == AssignmentType.EXPRESSION) {
                String value = String.valueOf(assignment.getValue());
                Matcher matcher = VARIABLE_PATTERN.matcher(value);
                while (matcher.find()) {
                    variables.add(matcher.group(1).trim());
                }
            }
        }
        return variables;
    }

    /**
     * 验证配置
     *
     * @return 验证结果
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        if (assignments == null || assignments.isEmpty()) {
            return ValidationResult.success();
        }

        for (int i = 0; i < assignments.size(); i++) {
            Assignment assignment = assignments.get(i);
            List<String> assignmentErrors = assignment.validate();
            for (String error : assignmentErrors) {
                errors.add(String.format("赋值 #%d: %s", i + 1, error));
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 变量赋值定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Assignment {

        /**
         * 目标变量名
         */
        private String variableName;

        /**
         * 赋值类型
         */
        private AssignmentType type;

        /**
         * 值（字面量或变量引用）
         * 变量引用格式: {{nodeName.variableName}}
         */
        private Object value;

        /**
         * 变量类型
         */
        private WorkflowValueType variableType;

        /**
         * 转换表达式（可选）
         * 用于对值进行转换处理
         */
        private String transformExpression;

        /**
         * 是否覆盖已存在的变量
         */
        private boolean overwrite;

        /**
         * 变量描述
         */
        private String description;

        /**
         * 验证赋值配置
         *
         * @return 错误信息列表
         */
        public List<String> validate() {
            List<String> errors = new ArrayList<>();

            if (variableName == null || variableName.isBlank()) {
                errors.add("变量名不能为空");
            } else if (!isValidVariableName(variableName)) {
                errors.add("变量名格式不正确，只能包含字母、数字和下划线，且不能以数字开头");
            }

            if (type == null) {
                errors.add("赋值类型不能为空");
            }

            if (type == AssignmentType.VARIABLE && value != null) {
                String valueStr = String.valueOf(value);
                if (!VARIABLE_PATTERN.matcher(valueStr).find()) {
                    errors.add("变量引用格式不正确，应为 {{nodeName.variableName}}");
                }
            }

            return errors;
        }

        /**
         * 检查变量名是否有效
         * @param name 名称
         * @return 处理结果
         */
        private boolean isValidVariableName(String name) {
            return name.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
        }

        /**
         * 转换值到目标类型
         *
         * @param resolvedValue 解析后的值
         * @return 转换后的值
         */
        public Object convertValue(Object resolvedValue) {
            if (resolvedValue == null || variableType == null) {
                return resolvedValue;
            }

            try {
                return switch (variableType) {
                    case STRING -> convertToString(resolvedValue);
                    case NUMBER -> convertToNumber(resolvedValue);
                    case BOOLEAN -> convertToBoolean(resolvedValue);
                    case ARRAY -> convertToArray(resolvedValue);
                    case OBJECT -> resolvedValue;
                    case ANY -> resolvedValue;
                };
            } catch (Exception e) {
                // 转换失败，返回原值
                return resolvedValue;
            }
        }

        private String convertToString(Object value) {
            return String.valueOf(value);
        }

        private Number convertToNumber(Object value) {
            if (value instanceof Number) {
                return (Number) value;
            }
            String str = String.valueOf(value);
            if (str.contains(".")) {
                return Double.parseDouble(str);
            }
            return Long.parseLong(str);
        }

        private Boolean convertToBoolean(Object value) {
            if (value instanceof Boolean) {
                return (Boolean) value;
            }
            String str = String.valueOf(value).toLowerCase();
            return "true".equals(str) || "1".equals(str) || "yes".equals(str);
        }

        @SuppressWarnings("unchecked")
        private List<Object> convertToArray(Object value) {
            if (value instanceof List) {
                return (List<Object>) value;
            }
            if (value instanceof Object[]) {
                return java.util.Arrays.asList((Object[]) value);
            }
            // 单个值转为数组
            List<Object> list = new ArrayList<>();
            list.add(value);
            return list;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Assignment that = (Assignment) o;
            return overwrite == that.overwrite
                    && Objects.equals(variableName, that.variableName)
                    && type == that.type
                    && Objects.equals(value, that.value)
                    && variableType == that.variableType
                    && Objects.equals(transformExpression, that.transformExpression)
                    && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(variableName, type, value, variableType, transformExpression, overwrite, description);
        }
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

        public static ValidationResult failure(String error) {
            List<String> errors = new ArrayList<>();
            errors.add(error);
            return new ValidationResult(false, errors);
        }
    }

    /**
     * 赋值类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum AssignmentType implements AiEnum {

        /**
         * 字面量赋值
         */
        LITERAL("LITERAL", "字面量"),

        /**
         * 变量引用
         */
        VARIABLE("VARIABLE", "变量引用"),

        /**
         * 表达式计算
         */
        EXPRESSION("EXPRESSION", "表达式");

        private final String code;
        private final String description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        VariableAssignerConfig that = (VariableAssignerConfig) o;
        return Objects.equals(assignments, that.assignments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(assignments);
    }
}
