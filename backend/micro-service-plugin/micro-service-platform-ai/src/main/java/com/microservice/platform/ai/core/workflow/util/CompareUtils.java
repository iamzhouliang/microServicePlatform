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

package com.microservice.platform.ai.core.workflow.util;

import com.microservice.platform.ai.core.workflow.enums.WorkflowCompareOperator;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 比较工具类
 * 提供统一的值比较逻辑，供条件节点等使用
 *
 * @author xJh
 * @since 2026/02/05
 */
@Slf4j
public final class CompareUtils {

    private CompareUtils() {
        // 工具类禁止实例化
    }

    /**
     * 使用指定运算符比较两个值
     *
     * @param left     左值
     * @param right    右值
     * @param operator 比较运算符
     * @return 比较结果
     */
    public static boolean compare(Object left, Object right, WorkflowCompareOperator operator) {
        try {
            return switch (operator) {
                case EQUALS -> objectEquals(left, right);
                case NOT_EQUALS -> !objectEquals(left, right);
                case GREATER_THAN -> compareNumbers(left, right) > 0;
                case LESS_THAN -> compareNumbers(left, right) < 0;
                case GREATER_OR_EQUAL -> compareNumbers(left, right) >= 0;
                case LESS_OR_EQUAL -> compareNumbers(left, right) <= 0;
                case CONTAINS -> stringContains(left, right);
                case NOT_CONTAINS -> !stringContains(left, right);
                case STARTS_WITH -> stringStartsWith(left, right);
                case ENDS_WITH -> stringEndsWith(left, right);
                case MATCHES_REGEX, MATCHES -> stringMatchesRegex(left, right);
                case IS_EMPTY -> isEmpty(left);
                case IS_NOT_EMPTY -> !isEmpty(left);
                case IS_NULL -> left == null;
                case IS_NOT_NULL -> left != null;
                case IN -> isInCollection(left, right);
                case NOT_IN -> !isInCollection(left, right);
            };
        } catch (Exception e) {
            log.warn("比较值失败: {} {} {}，原因: {}", left, operator, right, e.getMessage());
            return false;
        }
    }

    /**
     * 对象相等比较
     * @param left 左侧值
     * @param right 右侧值
     * @return 处理结果
     */
    public static boolean objectEquals(Object left, Object right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }

        // 数字比较（使用 BigDecimal 避免 double 精度丢失，如大整数、BigDecimal）
        if (left instanceof Number && right instanceof Number) {
            return toBigDecimal(left).compareTo(toBigDecimal(right)) == 0;
        }

        // 字符串比较（包括类型转换）
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    /**
     * 数字比较
     *
     * @param left 左侧值
     * @param right 右侧值
     * @return 负数表示 left < right，0 表示相等，正数表示 left > right
     */
    public static int compareNumbers(Object left, Object right) {
        return toBigDecimal(left).compareTo(toBigDecimal(right));
    }

    /**
     * 转换为 double
     * @param value 参数值
     * @return 处理结果
     */
    public static double toDouble(Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return Double.parseDouble(String.valueOf(value));
    }

    /**
     * 转换为 BigDecimal，保留精度，避免 double 相等/比较误差
     * @param value 参数值
     * @return 处理结果
     */
    public static BigDecimal toBigDecimal(Object value) {
        if (value instanceof BigDecimal bd) {
            return bd;
        }
        if (value instanceof Number number) {
            return new BigDecimal(number.toString());
        }
        return new BigDecimal(String.valueOf(value).trim());
    }

    /**
     * 字符串包含
     * @param left 左侧值
     * @param right 右侧值
     * @return 处理结果
     */
    public static boolean stringContains(Object left, Object right) {
        if (left == null) {
            return false;
        }
        return String.valueOf(left).contains(String.valueOf(right));
    }

    /**
     * 字符串开头匹配
     * @param left 左侧值
     * @param right 右侧值
     * @return 处理结果
     */
    public static boolean stringStartsWith(Object left, Object right) {
        if (left == null) {
            return false;
        }
        return String.valueOf(left).startsWith(String.valueOf(right));
    }

    /**
     * 字符串结尾匹配
     * @param left 左侧值
     * @param right 右侧值
     * @return 处理结果
     */
    public static boolean stringEndsWith(Object left, Object right) {
        if (left == null) {
            return false;
        }
        return String.valueOf(left).endsWith(String.valueOf(right));
    }

    /**
     * 正则表达式匹配
     * @param left 左侧值
     * @param right 右侧值
     * @return 处理结果
     */
    public static boolean stringMatchesRegex(Object left, Object right) {
        if (left == null || right == null) {
            return false;
        }
        return Pattern.matches(String.valueOf(right), String.valueOf(left));
    }

    /**
     * 判断是否为空
     * @param value 参数值
     * @return 处理结果
     */
    public static boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String str) {
            return str.isEmpty();
        }
        if (value instanceof List<?> list) {
            return list.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    /**
     * 判断值是否在集合中
     * @param collection collection 参数
     * @param value 参数值
     * @return 处理结果
     */
    public static boolean isInCollection(Object value, Object collection) {
        if (collection instanceof List<?> list) {
            return list.contains(value) || list.stream()
                    .anyMatch(item -> objectEquals(value, item));
        }
        if (collection instanceof String str) {
            // 检查值是否在逗号分隔的字符串中
            String[] items = str.split(",");
            for (String item : items) {
                if (objectEquals(value, item.trim())) {
                    return true;
                }
            }
        }
        return false;
    }
}
