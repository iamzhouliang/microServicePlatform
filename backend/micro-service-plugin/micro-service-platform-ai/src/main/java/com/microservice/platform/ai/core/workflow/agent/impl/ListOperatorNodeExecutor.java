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
import com.microservice.platform.ai.core.workflow.config.node.ListOperatorConfig;
import com.microservice.platform.ai.core.workflow.config.node.ListOperatorConfig.*;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.enums.WorkflowCompareOperator;
import com.microservice.platform.ai.core.workflow.enums.WorkflowLogicalOperator;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 列表操作节点执行器
 * 对数组进行过滤、排序、切片、提取等操作
 * 支持的操作:
 * - FILTER: 根据条件筛选元素
 * - SORT: 按指定字段排序
 * - SLICE: 获取数组的一部分
 * - EXTRACT: 提取数组元素的指定字段
 * - UNIQUE: 移除重复元素
 * - REVERSE: 反转数组顺序
 * - FLATTEN: 将嵌套数组展平
 * - CONCAT: 合并多个数组
 * - FIRST: 取第一个元素
 * - LAST: 取最后一个元素
 * - COUNT: 计数
 * - LIMIT: 限制数量
 *
 * @author xJh
 * @since 2026/01/08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ListOperatorNodeExecutor extends AbstractNodeExecutor {

    /**
     * 列表操作执行器（用于测试注入）
     */
    @Setter
    private ListOperator listOperator;

    /**
     * 列表操作接口
     */
    @FunctionalInterface
    public interface ListOperator {

        Object operate(List<?> input, ListOperatorConfig config, ExecutionContext context) throws Exception;
    }

    @Override
    public NodeType getType() {
        return NodeType.LIST_OPERATOR;
    }

    @Override
    public void validate(WorkflowNode node) {
        ListOperatorConfig config = parseConfig(node, ListOperatorConfig.class);

        if (config == null) {
            throw new IllegalArgumentException("列表操作节点缺少配置");
        }

        ValidationResult validationResult = config.validate();
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(
                    "列表操作节点配置无效: " + String.join(", ", validationResult.getErrors()));
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 LIST_OPERATOR 节点: {}", node.getId());

        ListOperatorConfig config = parseConfig(node, ListOperatorConfig.class);

        // 获取输入数组
        String inputVariable = config.getInputVariable();
        String varPath = resolveVariablePath(inputVariable);
        Object inputObj = context.getVariable(varPath, Object.class);

        if (inputObj == null) {
            return NodeExecutionResult.failure("输入数组为空，变量: " + varPath);
        }

        List<?> inputList;
        if (inputObj instanceof List<?> list) {
            inputList = list;
        } else if (inputObj.getClass().isArray()) {
            inputList = Arrays.asList((Object[]) inputObj);
        } else {
            return NodeExecutionResult.failure("输入不是数组: " + inputObj.getClass().getName());
        }

        try {
            Object result;
            if (listOperator != null) {
                result = listOperator.operate(inputList, config, context);
            } else {
                result = executeOperation(inputList, config, context);
            }

            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);

            String outputVariable = config.getOutputVariable();
            if (outputVariable != null && !outputVariable.isEmpty()) {
                outputs.put(outputVariable, result);
            }

            log.debug("LIST_OPERATOR 节点 {} 执行完成，操作类型 {}", node.getId(), config.getOperationType());
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("列表操作节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("列表操作失败: " + e.getMessage());
        }
    }

    private String resolveVariablePath(String variable) {
        if (variable == null) {
            return null;
        }
        String varPath = variable.trim();
        if (varPath.startsWith("{{") && varPath.endsWith("}}")) {
            varPath = varPath.substring(2, varPath.length() - 2).trim();
        }
        return varPath;
    }

    @SuppressWarnings("unchecked")
    private Object executeOperation(List<?> input, ListOperatorConfig config, ExecutionContext context) {
        OperationType opType = config.getOperationType();

        return switch (opType) {
            case FILTER -> executeFilter((List<Object>) input, config.getFilterConfig());
            case SORT -> executeSort((List<Object>) input, config.getSortConfig());
            case SLICE -> executeSlice(input, config.getSliceConfig());
            case EXTRACT -> executeExtract((List<Object>) input, config.getExtractConfig());
            case UNIQUE -> executeUnique((List<Object>) input, config.getUniqueConfig());
            case REVERSE -> executeReverse(input);
            case FLATTEN -> executeFlatten(input);
            case CONCAT -> executeConcat(input, config.getConcatConfig(), context);
            case FIRST -> executeFirst(input);
            case LAST -> executeLast(input);
            case COUNT -> input.size();
            case LIMIT -> executeLimit(input, config.getLimitConfig());
        };
    }

    private List<Object> executeFilter(List<Object> input, FilterConfig filterConfig) {
        if (filterConfig == null || filterConfig.getConditions() == null) {
            return input;
        }

        List<FilterCondition> conditions = filterConfig.getConditions();
        WorkflowLogicalOperator logicalOp = filterConfig.getOperator() != null ? filterConfig.getOperator() : WorkflowLogicalOperator.AND;

        return input.stream()
                .filter(item -> evaluateConditions(item, conditions, logicalOp))
                .collect(Collectors.toList());
    }

    private boolean evaluateConditions(Object item, List<FilterCondition> conditions, WorkflowLogicalOperator logicalOp) {
        if (logicalOp == WorkflowLogicalOperator.AND) {
            return conditions.stream().allMatch(cond -> evaluateCondition(item, cond));
        } else {
            return conditions.stream().anyMatch(cond -> evaluateCondition(item, cond));
        }
    }

    @SuppressWarnings("unchecked")
    private boolean evaluateCondition(Object item, FilterCondition condition) {
        Object fieldValue = getFieldValue(item, condition.getField());
        Object compareValue = condition.getValue();
        WorkflowCompareOperator op = condition.getOperator();

        return switch (op) {
            case EQUALS -> Objects.equals(fieldValue, compareValue);
            case NOT_EQUALS -> !Objects.equals(fieldValue, compareValue);
            case GREATER_THAN -> compareValues(fieldValue, compareValue) > 0;
            case LESS_THAN -> compareValues(fieldValue, compareValue) < 0;
            case GREATER_OR_EQUAL -> compareValues(fieldValue, compareValue) >= 0;
            case LESS_OR_EQUAL -> compareValues(fieldValue, compareValue) <= 0;
            case CONTAINS -> fieldValue != null && fieldValue.toString().contains(String.valueOf(compareValue));
            case NOT_CONTAINS -> fieldValue == null || !fieldValue.toString().contains(String.valueOf(compareValue));
            case STARTS_WITH -> fieldValue != null && fieldValue.toString().startsWith(String.valueOf(compareValue));
            case ENDS_WITH -> fieldValue != null && fieldValue.toString().endsWith(String.valueOf(compareValue));
            case IS_NULL -> fieldValue == null;
            case IS_NOT_NULL -> fieldValue != null;
            case IN -> compareValue instanceof Collection && ((Collection<?>) compareValue).contains(fieldValue);
            case NOT_IN -> !(compareValue instanceof Collection) || !((Collection<?>) compareValue).contains(fieldValue);
            case MATCHES_REGEX, MATCHES -> fieldValue != null && Pattern.matches(String.valueOf(compareValue), fieldValue.toString());
            case IS_EMPTY -> fieldValue == null || "".equals(fieldValue);
            case IS_NOT_EMPTY -> fieldValue != null && !"".equals(fieldValue);
        };
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }

        if (a instanceof Number && b instanceof Number) {
            // 使用 BigDecimal 比较，避免 double 对大整数/BigDecimal 的精度丢失（与 CompareUtils 保持一致）
            return new java.math.BigDecimal(a.toString()).compareTo(new java.math.BigDecimal(b.toString()));
        }
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    @SuppressWarnings("unchecked")
    private Object getFieldValue(Object item, String field) {
        if (field == null || field.isEmpty()) {
            return item;
        }
        if (item instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) item;
            String[] parts = field.split("\\.", 2);
            Object value = map.get(parts[0]);
            if (parts.length > 1 && value != null) {
                return getFieldValue(value, parts[1]);
            }
            return value;
        }
        return item;
    }

    @SuppressWarnings("unchecked")
    private List<Object> executeSort(List<Object> input, SortConfig sortConfig) {
        if (sortConfig == null) {
            return input;
        }

        String field = sortConfig.getField();
        SortDirection direction = sortConfig.getDirection() != null ? sortConfig.getDirection() : SortDirection.ASC;
        boolean ignoreCase = sortConfig.isIgnoreCase();

        List<Object> sorted = new ArrayList<>(input);
        sorted.sort((a, b) -> {
            Object valA = getFieldValue(a, field);
            Object valB = getFieldValue(b, field);

            int result;
            if (ignoreCase && valA instanceof String && valB instanceof String) {
                result = ((String) valA).compareToIgnoreCase((String) valB);
            } else {
                result = compareValues(valA, valB);
            }

            return direction == SortDirection.DESC ? -result : result;
        });

        return sorted;
    }

    private List<?> executeSlice(List<?> input, SliceConfig sliceConfig) {
        if (sliceConfig == null) {
            return input;
        }

        int size = input.size();
        int start = sliceConfig.getStart() != null ? normalizeIndex(sliceConfig.getStart(), size) : 0;
        int end = sliceConfig.getEnd() != null ? normalizeIndex(sliceConfig.getEnd(), size) : size;
        int step = sliceConfig.getStep() != null ? sliceConfig.getStep() : 1;

        if (start >= end || step <= 0) {
            return new ArrayList<>();
        }

        List<Object> result = new ArrayList<>();
        for (int i = start; i < end; i += step) {
            result.add(input.get(i));
        }
        return result;
    }

    private int normalizeIndex(int index, int size) {
        if (index < 0) {
            return Math.max(0, size + index);
        }
        return Math.min(index, size);
    }

    @SuppressWarnings("unchecked")
    private List<Object> executeExtract(List<Object> input, ExtractConfig extractConfig) {
        if (extractConfig == null || extractConfig.getFields() == null) {
            return input;
        }

        List<String> fields = extractConfig.getFields();
        boolean flatten = extractConfig.isFlatten() && fields.size() == 1;

        return input.stream()
                .map(item -> {
                    if (flatten) {
                        return getFieldValue(item, fields.get(0));
                    } else {
                        Map<String, Object> extracted = new LinkedHashMap<>();
                        for (String field : fields) {
                            extracted.put(field, getFieldValue(item, field));
                        }
                        return (Object) extracted;
                    }
                })
                .collect(Collectors.toList());
    }

    private List<Object> executeUnique(List<Object> input, UniqueConfig uniqueConfig) {
        String field = uniqueConfig != null ? uniqueConfig.getField() : null;
        KeepStrategy keepStrategy = uniqueConfig != null && uniqueConfig.getKeepStrategy() != null
                ? uniqueConfig.getKeepStrategy()
                : KeepStrategy.FIRST;

        if (keepStrategy == KeepStrategy.LAST) {
            List<Object> reversed = new ArrayList<>(input);
            Collections.reverse(reversed);
            List<Object> unique = uniqueByField(reversed, field);
            Collections.reverse(unique);
            return unique;
        }
        return uniqueByField(input, field);
    }

    private List<Object> uniqueByField(List<Object> input, String field) {
        Set<Object> seen = new LinkedHashSet<>();
        List<Object> result = new ArrayList<>();

        for (Object item : input) {
            Object key = field != null ? getFieldValue(item, field) : item;
            if (seen.add(key)) {
                result.add(item);
            }
        }
        return result;
    }

    private List<?> executeReverse(List<?> input) {
        List<Object> result = new ArrayList<>(input);
        Collections.reverse(result);
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> executeFlatten(List<?> input) {
        List<Object> result = new ArrayList<>();
        for (Object item : input) {
            if (item instanceof List) {
                result.addAll(executeFlatten((List<?>) item));
            } else {
                result.add(item);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Object> executeConcat(List<?> input, ConcatConfig concatConfig, ExecutionContext context) {
        List<Object> result = new ArrayList<>(input);

        if (concatConfig != null && concatConfig.getOtherArrays() != null) {
            for (String arrayVar : concatConfig.getOtherArrays()) {
                String varPath = resolveVariablePath(arrayVar);
                Object otherArray = context.getVariable(varPath, Object.class);
                if (otherArray instanceof List) {
                    result.addAll((List<?>) otherArray);
                }
            }
        }

        if (concatConfig != null && concatConfig.isRemoveDuplicates()) {
            return uniqueByField(result, null);
        }
        return result;
    }

    private Object executeFirst(List<?> input) {
        return input.isEmpty() ? null : input.get(0);
    }

    private Object executeLast(List<?> input) {
        return input.isEmpty() ? null : input.get(input.size() - 1);
    }

    private List<?> executeLimit(List<?> input, LimitConfig limitConfig) {
        if (limitConfig == null) {
            return input;
        }

        // 防御负数：offset/count 为负会导致 subList 抛越界/非法参数异常
        int offset = Math.max(0, limitConfig.getOffset() != null ? limitConfig.getOffset() : 0);
        int count = Math.max(0, limitConfig.getCount() != null ? limitConfig.getCount() : input.size());

        if (offset >= input.size() || count == 0) {
            return new ArrayList<>();
        }

        int end = Math.min(offset + count, input.size());
        // 返回独立副本而非 subList 视图，避免下游修改回写原列表
        return new ArrayList<>(input.subList(offset, end));
    }
}
