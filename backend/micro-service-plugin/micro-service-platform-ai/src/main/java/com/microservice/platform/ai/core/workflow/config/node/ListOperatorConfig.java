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
import com.microservice.platform.ai.core.workflow.enums.WorkflowCompareOperator;
import com.microservice.platform.ai.core.workflow.enums.WorkflowLogicalOperator;
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
 * 列表操作节点配置
 * 对数组进行过滤、排序、切片、提取等操作
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListOperatorConfig {

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 输入数组变量
     * 支持变量引用: {{nodeName.arrayVariable}}
     */
    private String inputVariable;

    /**
     * 操作类型
     */
    private OperationType operationType;

    /**
     * 输出变量名
     */
    private String outputVariable;

    /**
     * 过滤配置（FILTER 操作使用）
     */
    private FilterConfig filterConfig;

    /**
     * 排序配置（SORT 操作使用）
     */
    private SortConfig sortConfig;

    /**
     * 切片配置（SLICE 操作使用）
     */
    private SliceConfig sliceConfig;

    /**
     * 提取配置（EXTRACT 操作使用）
     */
    private ExtractConfig extractConfig;

    /**
     * 去重配置（UNIQUE 操作使用）
     */
    private UniqueConfig uniqueConfig;

    /**
     * 限制数量配置（LIMIT 操作使用）
     */
    private LimitConfig limitConfig;

    /**
     * 合并配置（CONCAT 操作使用）
     */
    private ConcatConfig concatConfig;

    /**
     * 操作类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum OperationType implements AiEnum {

        /**
         * 过滤
         * 根据条件筛选元素
         */
        FILTER("FILTER", "过滤"),

        /**
         * 排序
         * 按指定字段排序
         */
        SORT("SORT", "排序"),

        /**
         * 切片
         * 获取数组的一部分
         */
        SLICE("SLICE", "切片"),

        /**
         * 提取
         * 提取数组元素的指定字段
         */
        EXTRACT("EXTRACT", "提取"),

        /**
         * 去重
         * 移除重复元素
         */
        UNIQUE("UNIQUE", "去重"),

        /**
         * 反转
         * 反转数组顺序
         */
        REVERSE("REVERSE", "反转"),

        /**
         * 扁平化
         * 将嵌套数组展平
         */
        FLATTEN("FLATTEN", "扁平化"),

        /**
         * 合并
         * 合并多个数组
         */
        CONCAT("CONCAT", "合并"),

        /**
         * 取第一个
         */
        FIRST("FIRST", "取第一个"),

        /**
         * 取最后一个
         */
        LAST("LAST", "取最后一个"),

        /**
         * 计数
         */
        COUNT("COUNT", "计数"),

        /**
         * 限制数量
         */
        LIMIT("LIMIT", "限制数量");

        private final String code;
        private final String description;
    }

    /**
     * 过滤配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterConfig {

        /**
         * 过滤条件列表
         */
        private List<FilterCondition> conditions;

        /**
         * 条件组合方式
         */
        private WorkflowLogicalOperator operator;
    }

    /**
     * 过滤条件
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FilterCondition {

        /**
         * 字段路径
         * 如: name, address.city
         */
        private String field;

        /**
         * 比较运算符
         */
        private WorkflowCompareOperator operator;

        /**
         * 比较值
         */
        private Object value;
    }

    /**
     * 排序配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortConfig {

        /**
         * 排序字段
         */
        private String field;

        /**
         * 排序方向
         */
        private SortDirection direction;

        /**
         * 是否忽略大小写（字符串排序）
         */
        private boolean ignoreCase;
    }

    /**
     * 排序方向
     */
    @Getter
    @AllArgsConstructor
    public enum SortDirection implements AiEnum {

        ASC("ASC", "升序"),
        DESC("DESC", "降序");

        private final String code;
        private final String description;
    }

    /**
     * 切片配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SliceConfig {

        /**
         * 起始索引（包含）
         */
        private Integer start;

        /**
         * 结束索引（不包含）
         */
        private Integer end;

        /**
         * 步长
         */
        private Integer step;
    }

    /**
     * 提取配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractConfig {

        /**
         * 要提取的字段列表
         */
        private List<String> fields;

        /**
         * 是否扁平化（单字段时）
         */
        private boolean flatten;
    }

    /**
     * 去重配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UniqueConfig {

        /**
         * 去重依据的字段
         * 为空时按整个元素去重
         */
        private String field;

        /**
         * 保留策略
         */
        private KeepStrategy keepStrategy;
    }

    /**
     * 保留策略
     */
    @Getter
    @AllArgsConstructor
    public enum KeepStrategy implements AiEnum {

        FIRST("FIRST", "保留第一个"),
        LAST("LAST", "保留最后一个");

        private final String code;
        private final String description;
    }

    /**
     * 限制数量配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LimitConfig {

        /**
         * 限制数量
         */
        private Integer count;

        /**
         * 偏移量
         */
        private Integer offset;
    }

    /**
     * 合并配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConcatConfig {

        /**
         * 要合并的其他数组变量
         */
        private List<String> otherArrays;

        /**
         * 是否去重
         */
        private boolean removeDuplicates;
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

        // 验证输入变量
        if (inputVariable == null || inputVariable.trim().isEmpty()) {
            errors.add("输入数组变量不能为空");
        }

        // 验证操作类型
        if (operationType == null) {
            errors.add("操作类型不能为空");
        }

        // 验证输出变量名
        if (outputVariable == null || outputVariable.trim().isEmpty()) {
            errors.add("输出变量名不能为空");
        }

        // 根据操作类型验证相应配置
        if (operationType != null) {
            switch (operationType) {
                case FILTER:
                    if (filterConfig == null || filterConfig.getConditions() == null || filterConfig.getConditions().isEmpty()) {
                        errors.add("FILTER 操作需要配置过滤条件");
                    }
                    break;
                case SORT:
                    if (sortConfig == null || sortConfig.getField() == null || sortConfig.getField().trim().isEmpty()) {
                        errors.add("SORT 操作需要配置排序字段");
                    }
                    break;
                case SLICE:
                    if (sliceConfig == null) {
                        errors.add("SLICE 操作需要配置切片参数");
                    } else {
                        if (sliceConfig.getStart() != null && sliceConfig.getEnd() != null
                                && sliceConfig.getStart() > sliceConfig.getEnd()) {
                            errors.add("切片起始索引不能大于结束索引");
                        }
                        if (sliceConfig.getStep() != null && sliceConfig.getStep() <= 0) {
                            errors.add("切片步长必须大于0");
                        }
                    }
                    break;
                case EXTRACT:
                    if (extractConfig == null || extractConfig.getFields() == null || extractConfig.getFields().isEmpty()) {
                        errors.add("EXTRACT 操作需要配置要提取的字段");
                    }
                    break;
                case LIMIT:
                    if (limitConfig == null || limitConfig.getCount() == null || limitConfig.getCount() <= 0) {
                        errors.add("LIMIT 操作需要配置有效的限制数量");
                    }
                    break;
                case CONCAT:
                    if (concatConfig == null || concatConfig.getOtherArrays() == null || concatConfig.getOtherArrays().isEmpty()) {
                        errors.add("CONCAT 操作需要配置要合并的其他数组");
                    }
                    break;
                default:
                    // REVERSE, FLATTEN, FIRST, LAST, COUNT, UNIQUE 不需要额外配置
                    break;
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
        List<String> variables = new ArrayList<>();

        // 从输入变量提取
        if (inputVariable != null) {
            Matcher matcher = VARIABLE_PATTERN.matcher(inputVariable);
            while (matcher.find()) {
                variables.add(matcher.group(1).trim());
            }
        }

        // 从合并配置提取
        if (concatConfig != null && concatConfig.getOtherArrays() != null) {
            for (String arrayVar : concatConfig.getOtherArrays()) {
                Matcher matcher = VARIABLE_PATTERN.matcher(arrayVar);
                while (matcher.find()) {
                    variables.add(matcher.group(1).trim());
                }
            }
        }

        return variables;
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
     * 检查操作是否会改变数组长度
     *
     * @return 是否会改变数组长度
     */
    public boolean changesArrayLength() {
        if (operationType == null) {
            return false;
        }
        return switch (operationType) {
            case FILTER, SLICE, UNIQUE, LIMIT, CONCAT, FLATTEN -> true;
            case SORT, REVERSE, EXTRACT -> false;
            // 这些返回单个元素或数字
            case FIRST, LAST, COUNT -> true;
        };
    }

    /**
     * 检查操作是否返回单个值（非数组）
     *
     * @return 是否返回单个值
     */
    public boolean returnsSingleValue() {
        if (operationType == null) {
            return false;
        }
        return switch (operationType) {
            case FIRST, LAST, COUNT -> true;
            default -> false;
        };
    }
}
