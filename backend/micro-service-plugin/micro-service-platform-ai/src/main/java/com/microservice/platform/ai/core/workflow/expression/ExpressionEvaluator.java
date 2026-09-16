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

package com.microservice.platform.ai.core.workflow.expression;

import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 表达式评估器
 * 使用 Spring Expression Language (SpEL) 评估条件表达式
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
public class ExpressionEvaluator {

    /**
     * 简单比较表达式模式
     * 支持: value > 10, value == 'test', value != null 等
     */
    private static final Pattern SIMPLE_COMPARISON_PATTERN =
            Pattern.compile("^\\s*([\\w.]+)\\s*(==|!=|>|<|>=|<=|contains|startsWith|endsWith)\\s*(.+)\\s*$");

    private final ExpressionParser parser = new SpelExpressionParser();

    /**
     * 评估布尔表达式
     *
     * @param expression 表达式字符串
     * @param variables  变量映射
     * @return 评估结果
     * @throws ExpressionEvaluationException 处理失败时抛出
     */
    public boolean evaluate(String expression, Map<String, Object> variables) {
        if (expression == null || expression.trim().isEmpty()) {
            return false;
        }

        try {
            // 尝试简单比较表达式
            Boolean simpleResult = evaluateSimpleComparison(expression, variables);
            if (simpleResult != null) {
                return simpleResult;
            }

            // 使用 SpEL 评估复杂表达式
            return evaluateSpel(expression, variables);
        } catch (Exception e) {
            log.error("表达式 '{}' 计算失败: {}", expression, e.getMessage());
            throw new ExpressionEvaluationException("表达式计算失败: " + e.getMessage(), e);
        }
    }

    /**
     * 评估简单比较表达式
     * @param variables 变量集合
     * @param expression expression 参数
     * @return 处理结果
     */
    private Boolean evaluateSimpleComparison(String expression, Map<String, Object> variables) {
        Matcher matcher = SIMPLE_COMPARISON_PATTERN.matcher(expression);
        if (!matcher.matches()) {
            return null;
        }

        String leftOperand = matcher.group(1).trim();
        String operator = matcher.group(2).trim();
        String rightOperand = matcher.group(3).trim();

        // 获取左操作数的值
        Object leftValue = resolveValue(leftOperand, variables);
        // 解析右操作数
        Object rightValue = parseValue(rightOperand, variables);

        return compare(leftValue, operator, rightValue);
    }

    /**
     * 解析变量值
     * @param variables 变量集合
     * @param name 名称
     * @return 处理结果
     */
    private Object resolveValue(String name, Map<String, Object> variables) {
        // 支持点号分隔的嵌套访问
        String[] parts = name.split("\\.");
        Object value = variables.get(parts[0]);

        for (int i = 1; i < parts.length && value != null; i++) {
            if (value instanceof Map) {
                value = ((Map<?, ?>) value).get(parts[i]);
            } else {
                // 尝试使用反射访问属性
                try {
                    java.lang.reflect.Field field = value.getClass().getDeclaredField(parts[i]);
                    field.setAccessible(true);
                    value = field.get(value);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return value;
    }

    /**
     * 解析字面量值
     * @param value 参数值
     * @param variables 变量集合
     * @return 处理结果
     */
    private Object parseValue(String value, Map<String, Object> variables) {
        if (value == null) {
            return null;
        }

        String normalizedValue = value.trim();

        // null
        if ("null".equalsIgnoreCase(normalizedValue)) {
            return null;
        }

        // 布尔值
        if ("true".equalsIgnoreCase(normalizedValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(normalizedValue)) {
            return false;
        }

        // 字符串（带引号）
        if ((normalizedValue.startsWith("'") && normalizedValue.endsWith("'"))
                || (normalizedValue.startsWith("\"") && normalizedValue.endsWith("\""))) {
            return normalizedValue.substring(1, normalizedValue.length() - 1);
        }

        // 数字
        try {
            if (normalizedValue.contains(".")) {
                return Double.parseDouble(normalizedValue);
            }
            return Long.parseLong(normalizedValue);
        } catch (NumberFormatException e) {
            // 不是数字，尝试作为变量引用
            return resolveValue(normalizedValue, variables);
        }
    }

    /**
     * 执行比较操作
     * @param left 左侧值
     * @param operator operator 参数
     * @param right 右侧值
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean compare(Object left, String operator, Object right) {
        switch (operator) {
            case "==" -> {
                if (left == null && right == null) {
                    return true;
                }
                if (left == null || right == null) {
                    return false;
                }
                return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
            }
            case "!=" -> {
                if (left == null && right == null) {
                    return false;
                }
                if (left == null || right == null) {
                    return true;
                }
                return !left.equals(right) && !String.valueOf(left).equals(String.valueOf(right));
            }
            case ">" -> {
                if (left == null || right == null) {
                    return false;
                }
                if (left instanceof Comparable && right instanceof Comparable) {
                    return ((Comparable) left).compareTo(convertToSameType(right, left)) > 0;
                }
                return false;
            }
            case "<" -> {
                if (left == null || right == null) {
                    return false;
                }
                if (left instanceof Comparable && right instanceof Comparable) {
                    return ((Comparable) left).compareTo(convertToSameType(right, left)) < 0;
                }
                return false;
            }
            case ">=" -> {
                if (left == null || right == null) {
                    return false;
                }
                if (left instanceof Comparable && right instanceof Comparable) {
                    return ((Comparable) left).compareTo(convertToSameType(right, left)) >= 0;
                }
                return false;
            }
            case "<=" -> {
                if (left == null || right == null) {
                    return false;
                }
                if (left instanceof Comparable && right instanceof Comparable) {
                    return ((Comparable) left).compareTo(convertToSameType(right, left)) <= 0;
                }
                return false;
            }
            case "contains" -> {
                if (left == null || right == null) {
                    return false;
                }
                return String.valueOf(left).contains(String.valueOf(right));
            }
            case "startsWith" -> {
                if (left == null || right == null) {
                    return false;
                }
                return String.valueOf(left).startsWith(String.valueOf(right));
            }
            case "endsWith" -> {
                if (left == null || right == null) {
                    return false;
                }
                return String.valueOf(left).endsWith(String.valueOf(right));
            }
            default -> throw new IllegalArgumentException("未知运算符: " + operator);
        }
    }

    /**
     * 转换为相同类型
     * @param target target 参数
     * @param value 参数值
     * @return 处理结果
     */
    private Object convertToSameType(Object value, Object target) {
        if (target instanceof Double) {
            return ((Number) value).doubleValue();
        }
        if (target instanceof Long) {
            return ((Number) value).longValue();
        }
        if (target instanceof Integer) {
            return ((Number) value).intValue();
        }
        return value;
    }

    /**
     * 使用 SpEL 评估表达式
     * 使用 {@link SimpleEvaluationContext} 只读数据绑定上下文，禁止类型引用（T(...)）、构造器、
     * Bean 引用与赋值等能力，防止用户在工作流条件中通过 SpEL 执行任意代码（RCE）。
     * @param expression expression 参数
     * @param variables 变量集合
     * @return 处理结果
     */
    private boolean evaluateSpel(String expression, Map<String, Object> variables) {
        SimpleEvaluationContext context = SimpleEvaluationContext.forReadOnlyDataBinding().build();

        // 将变量添加到上下文（仅数据，禁止类型/构造器/Bean 访问）
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }

        Expression exp = parser.parseExpression(expression);
        Boolean result = exp.getValue(context, Boolean.class);
        return result != null && result;
    }

    /**
     * 表达式评估异常
     */
    public static class ExpressionEvaluationException extends RuntimeException {

        public ExpressionEvaluationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
