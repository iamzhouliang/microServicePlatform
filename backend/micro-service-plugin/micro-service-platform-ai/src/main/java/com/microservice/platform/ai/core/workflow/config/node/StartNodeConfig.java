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
import java.util.List;
import java.util.Objects;

/**
 * START 节点配置
 * 定义工作流输入字段，支持多种输入类型
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StartNodeConfig {

    /**
     * 输入字段列表
     */
    private List<InputField> fields;

    /**
     * 验证输入数据
     *
     * @param inputData 输入数据
     * @return 验证结果
     */
    public ValidationResult validate(java.util.Map<String, Object> inputData) {
        List<String> errors = new ArrayList<>();

        if (fields == null || fields.isEmpty()) {
            return ValidationResult.success();
        }

        for (InputField field : fields) {
            Object value = inputData != null ? inputData.get(field.getName()) : null;
            List<String> fieldErrors = field.validate(value);
            errors.addAll(fieldErrors);
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 输入字段定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class InputField {

        /**
         * 字段名（变量名）
         */
        private String name;

        /**
         * 显示标签
         */
        private String label;

        /**
         * 字段类型
         */
        private InputFieldType type;

        /**
         * 是否必填
         */
        private boolean required;

        /**
         * 默认值
         */
        private Object defaultValue;

        /**
         * 字段描述
         */
        private String description;

        /**
         * 下拉选项（SELECT 类型使用）
         */
        private List<String> options;

        /**
         * 最大长度（文本类型使用）
         */
        private Integer maxLength;

        /**
         * 最小值（NUMBER 类型使用）
         */
        private Number minValue;

        /**
         * 最大值（NUMBER 类型使用）
         */
        private Number maxValue;

        /**
         * 允许的文件类型（文件类型使用）
         */
        private List<String> allowedFileTypes;

        /**
         * 最大文件大小（字节）
         */
        private Long maxFileSize;

        /**
         * 最大文件数量（FILE_LIST 类型使用）
         */
        private Integer maxFileCount;

        /**
         * 正则表达式验证（文本类型使用）
         */
        private String pattern;

        /**
         * 正则表达式验证失败提示
         */
        private String patternMessage;

        /**
         * 验证字段值
         *
         * @param value 字段值
         * @return 错误信息列表，空列表表示验证通过
         */
        public List<String> validate(Object value) {
            List<String> errors = new ArrayList<>();
            String fieldLabel = label != null ? label : name;

            // 必填验证
            if (required && (value == null || (value instanceof String && ((String) value).isBlank()))) {
                errors.add(String.format("字段 '%s' 是必填项", fieldLabel));
                return errors;
            }

            if (value == null) {
                return errors;
            }

            // 根据类型进行验证
            switch (type) {
                case SHORT_TEXT, PARAGRAPH -> validateText(value, errors, fieldLabel);
                case NUMBER -> validateNumber(value, errors, fieldLabel);
                case SELECT -> validateSelect(value, errors, fieldLabel);
                case CHECKBOX -> validateCheckbox(value, errors, fieldLabel);
                case SINGLE_FILE -> validateSingleFile(value, errors, fieldLabel);
                case FILE_LIST -> validateFileList(value, errors, fieldLabel);
                default -> errors.add(String.format("字段 '%s' 使用了不支持的类型: %s", fieldLabel, type));
            }

            return errors;
        }

        private void validateText(Object value, List<String> errors, String fieldLabel) {
            if (!(value instanceof String)) {
                errors.add(String.format("字段 '%s' 必须是文本类型", fieldLabel));
                return;
            }
            String text = (String) value;

            // 长度验证
            Integer effectiveMaxLength = maxLength != null ? maxLength : type.getDefaultMaxLength();
            if (effectiveMaxLength != null && text.length() > effectiveMaxLength) {
                errors.add(String.format("字段 '%s' 长度不能超过 %d 个字符", fieldLabel, effectiveMaxLength));
            }

            // 正则验证
            if (pattern != null && !text.matches(pattern)) {
                String message = patternMessage != null ? patternMessage : String.format("字段 '%s' 格式不正确", fieldLabel);
                errors.add(message);
            }
        }

        private void validateNumber(Object value, List<String> errors, String fieldLabel) {
            Number number;
            if (value instanceof Number) {
                number = (Number) value;
            } else if (value instanceof String) {
                try {
                    number = Double.parseDouble((String) value);
                } catch (NumberFormatException e) {
                    errors.add(String.format("字段 '%s' 必须是数字类型", fieldLabel));
                    return;
                }
            } else {
                errors.add(String.format("字段 '%s' 必须是数字类型", fieldLabel));
                return;
            }

            if (minValue != null && number.doubleValue() < minValue.doubleValue()) {
                errors.add(String.format("字段 '%s' 不能小于 %s", fieldLabel, minValue));
            }
            if (maxValue != null && number.doubleValue() > maxValue.doubleValue()) {
                errors.add(String.format("字段 '%s' 不能大于 %s", fieldLabel, maxValue));
            }
        }

        private void validateSelect(Object value, List<String> errors, String fieldLabel) {
            if (options == null || options.isEmpty()) {
                return;
            }
            String strValue = String.valueOf(value);
            if (!options.contains(strValue)) {
                errors.add(String.format("字段 '%s' 的值必须是以下选项之一: %s", fieldLabel, String.join(", ", options)));
            }
        }

        private void validateCheckbox(Object value, List<String> errors, String fieldLabel) {
            if (!(value instanceof Boolean) && !(value instanceof String)) {
                errors.add(String.format("字段 '%s' 必须是布尔类型", fieldLabel));
            }
        }

        private void validateSingleFile(Object value, List<String> errors, String fieldLabel) {
            // 文件验证逻辑（实际验证在文件上传时进行）
            if (value instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> fileInfo = (java.util.Map<String, Object>) value;
                validateFileInfo(fileInfo, errors, fieldLabel);
            }
        }

        private void validateFileList(Object value, List<String> errors, String fieldLabel) {
            if (!(value instanceof List)) {
                errors.add(String.format("字段 '%s' 必须是文件列表", fieldLabel));
                return;
            }
            @SuppressWarnings("unchecked")
            List<Object> files = (List<Object>) value;

            if (maxFileCount != null && files.size() > maxFileCount) {
                errors.add(String.format("字段 '%s' 最多只能上传 %d 个文件", fieldLabel, maxFileCount));
            }

            for (Object file : files) {
                if (file instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> fileInfo = (java.util.Map<String, Object>) file;
                    validateFileInfo(fileInfo, errors, fieldLabel);
                }
            }
        }

        private void validateFileInfo(java.util.Map<String, Object> fileInfo, List<String> errors, String fieldLabel) {
            // 文件类型验证
            if (allowedFileTypes != null && !allowedFileTypes.isEmpty()) {
                String fileName = (String) fileInfo.get("name");
                if (fileName != null) {
                    String extension = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf(".") + 1).toLowerCase() : "";
                    boolean allowed = allowedFileTypes.stream().anyMatch(type -> type.equalsIgnoreCase(extension) || type.equalsIgnoreCase("." + extension));
                    if (!allowed) {
                        errors.add(String.format("字段 '%s' 不支持该文件类型，允许的类型: %s", fieldLabel, String.join(", ", allowedFileTypes)));
                    }
                }
            }

            // 文件大小验证
            if (maxFileSize != null) {
                Object sizeObj = fileInfo.get("size");
                if (sizeObj instanceof Number) {
                    long size = ((Number) sizeObj).longValue();
                    if (size > maxFileSize) {
                        errors.add(String.format("字段 '%s' 文件大小不能超过 %d 字节", fieldLabel, maxFileSize));
                    }
                }
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            InputField that = (InputField) o;
            return required == that.required
                    && Objects.equals(name, that.name)
                    && Objects.equals(label, that.label)
                    && type == that.type
                    && Objects.equals(defaultValue, that.defaultValue)
                    && Objects.equals(description, that.description)
                    && Objects.equals(options, that.options)
                    && Objects.equals(maxLength, that.maxLength)
                    && Objects.equals(minValue, that.minValue)
                    && Objects.equals(maxValue, that.maxValue)
                    && Objects.equals(allowedFileTypes, that.allowedFileTypes)
                    && Objects.equals(maxFileSize, that.maxFileSize)
                    && Objects.equals(maxFileCount, that.maxFileCount)
                    && Objects.equals(pattern, that.pattern)
                    && Objects.equals(patternMessage, that.patternMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, label, type, required, defaultValue, description, options,
                    maxLength, minValue, maxValue, allowedFileTypes, maxFileSize, maxFileCount, pattern, patternMessage);
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
     * 输入字段类型枚举
     * 参考 Dify: https://docs.dify.ai/en/guides/workflow/node/start
     */
    @Getter
    @AllArgsConstructor
    public enum InputFieldType implements AiEnum {

        /**
         * 短文本（最大 256 字符）
         */
        SHORT_TEXT("SHORT_TEXT", "短文本", 256),

        /**
         * 长文本/段落（无限制）
         */
        PARAGRAPH("PARAGRAPH", "长文本", Integer.MAX_VALUE),

        /**
         * 数字
         */
        NUMBER("NUMBER", "数字", null),

        /**
         * 下拉选择
         */
        SELECT("SELECT", "下拉选择", null),

        /**
         * 复选框
         */
        CHECKBOX("CHECKBOX", "复选框", null),

        /**
         * 单文件上传
         */
        SINGLE_FILE("SINGLE_FILE", "单文件", null),

        /**
         * 多文件上传
         */
        FILE_LIST("FILE_LIST", "多文件", null);

        private final String code;
        private final String description;
        private final Integer defaultMaxLength;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        StartNodeConfig that = (StartNodeConfig) o;
        return Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fields);
    }
}
