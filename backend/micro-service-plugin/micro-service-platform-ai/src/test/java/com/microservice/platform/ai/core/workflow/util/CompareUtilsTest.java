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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CompareUtils 单元测试
 *
 * @author xJh
 * @since 2026/02/05
 */
@DisplayName("CompareUtils 测试")
class CompareUtilsTest {

    @Nested
    @DisplayName("EQUALS 运算符测试")
    class EqualsTests {

        @Test
        @DisplayName("相同字符串应相等")
        void sameStringsShouldBeEqual() {
            assertTrue(CompareUtils.compare("hello", "hello", WorkflowCompareOperator.EQUALS));
        }

        @Test
        @DisplayName("不同字符串应不相等")
        void differentStringsShouldNotBeEqual() {
            assertFalse(CompareUtils.compare("hello", "world", WorkflowCompareOperator.EQUALS));
        }

        @Test
        @DisplayName("相同数字应相等")
        void sameNumbersShouldBeEqual() {
            assertTrue(CompareUtils.compare(10, 10, WorkflowCompareOperator.EQUALS));
            assertTrue(CompareUtils.compare(10.5, 10.5, WorkflowCompareOperator.EQUALS));
        }

        @Test
        @DisplayName("不同类型但值相同的数字应相等")
        void differentTypeNumbersShouldBeEqual() {
            assertTrue(CompareUtils.compare(10, 10.0, WorkflowCompareOperator.EQUALS));
            assertTrue(CompareUtils.compare(10L, 10, WorkflowCompareOperator.EQUALS));
        }

        @Test
        @DisplayName("null 与 null 应相等")
        void nullsShouldBeEqual() {
            assertTrue(CompareUtils.compare(null, null, WorkflowCompareOperator.EQUALS));
        }

        @Test
        @DisplayName("null 与非 null 应不相等")
        void nullAndNonNullShouldNotBeEqual() {
            assertFalse(CompareUtils.compare(null, "hello", WorkflowCompareOperator.EQUALS));
            assertFalse(CompareUtils.compare("hello", null, WorkflowCompareOperator.EQUALS));
        }
    }

    @Nested
    @DisplayName("数字比较运算符测试")
    class NumericComparisonTests {

        @Test
        @DisplayName("GREATER_THAN 应正确比较")
        void greaterThanShouldWork() {
            assertTrue(CompareUtils.compare(10, 5, WorkflowCompareOperator.GREATER_THAN));
            assertFalse(CompareUtils.compare(5, 10, WorkflowCompareOperator.GREATER_THAN));
            assertFalse(CompareUtils.compare(5, 5, WorkflowCompareOperator.GREATER_THAN));
        }

        @Test
        @DisplayName("LESS_THAN 应正确比较")
        void lessThanShouldWork() {
            assertTrue(CompareUtils.compare(5, 10, WorkflowCompareOperator.LESS_THAN));
            assertFalse(CompareUtils.compare(10, 5, WorkflowCompareOperator.LESS_THAN));
            assertFalse(CompareUtils.compare(5, 5, WorkflowCompareOperator.LESS_THAN));
        }

        @Test
        @DisplayName("GREATER_OR_EQUAL 应正确比较")
        void greaterOrEqualShouldWork() {
            assertTrue(CompareUtils.compare(10, 5, WorkflowCompareOperator.GREATER_OR_EQUAL));
            assertTrue(CompareUtils.compare(5, 5, WorkflowCompareOperator.GREATER_OR_EQUAL));
            assertFalse(CompareUtils.compare(5, 10, WorkflowCompareOperator.GREATER_OR_EQUAL));
        }

        @Test
        @DisplayName("LESS_OR_EQUAL 应正确比较")
        void lessOrEqualShouldWork() {
            assertTrue(CompareUtils.compare(5, 10, WorkflowCompareOperator.LESS_OR_EQUAL));
            assertTrue(CompareUtils.compare(5, 5, WorkflowCompareOperator.LESS_OR_EQUAL));
            assertFalse(CompareUtils.compare(10, 5, WorkflowCompareOperator.LESS_OR_EQUAL));
        }
    }

    @Nested
    @DisplayName("字符串运算符测试")
    class StringOperatorTests {

        @Test
        @DisplayName("CONTAINS 应正确判断包含关系")
        void containsShouldWork() {
            assertTrue(CompareUtils.compare("hello world", "world", WorkflowCompareOperator.CONTAINS));
            assertFalse(CompareUtils.compare("hello", "world", WorkflowCompareOperator.CONTAINS));
            assertFalse(CompareUtils.compare(null, "world", WorkflowCompareOperator.CONTAINS));
        }

        @Test
        @DisplayName("STARTS_WITH 应正确判断前缀")
        void startsWithShouldWork() {
            assertTrue(CompareUtils.compare("hello world", "hello", WorkflowCompareOperator.STARTS_WITH));
            assertFalse(CompareUtils.compare("hello world", "world", WorkflowCompareOperator.STARTS_WITH));
        }

        @Test
        @DisplayName("ENDS_WITH 应正确判断后缀")
        void endsWithShouldWork() {
            assertTrue(CompareUtils.compare("hello world", "world", WorkflowCompareOperator.ENDS_WITH));
            assertFalse(CompareUtils.compare("hello world", "hello", WorkflowCompareOperator.ENDS_WITH));
        }

        @Test
        @DisplayName("MATCHES_REGEX 应正确匹配正则")
        void matchesRegexShouldWork() {
            assertTrue(CompareUtils.compare("hello123", ".*\\d+", WorkflowCompareOperator.MATCHES_REGEX));
            assertFalse(CompareUtils.compare("hello", ".*\\d+", WorkflowCompareOperator.MATCHES_REGEX));
        }
    }

    @Nested
    @DisplayName("空值运算符测试")
    class EmptyOperatorTests {

        @Test
        @DisplayName("IS_EMPTY 应正确判断空值")
        void isEmptyShouldWork() {
            assertTrue(CompareUtils.compare(null, null, WorkflowCompareOperator.IS_EMPTY));
            assertTrue(CompareUtils.compare("", null, WorkflowCompareOperator.IS_EMPTY));
            assertTrue(CompareUtils.compare(Collections.emptyList(), null, WorkflowCompareOperator.IS_EMPTY));
            assertTrue(CompareUtils.compare(new HashMap<>(), null, WorkflowCompareOperator.IS_EMPTY));
            assertFalse(CompareUtils.compare("hello", null, WorkflowCompareOperator.IS_EMPTY));
            assertFalse(CompareUtils.compare(List.of(1, 2), null, WorkflowCompareOperator.IS_EMPTY));
        }

        @Test
        @DisplayName("IS_NOT_EMPTY 应正确判断非空值")
        void isNotEmptyShouldWork() {
            assertFalse(CompareUtils.compare(null, null, WorkflowCompareOperator.IS_NOT_EMPTY));
            assertFalse(CompareUtils.compare("", null, WorkflowCompareOperator.IS_NOT_EMPTY));
            assertTrue(CompareUtils.compare("hello", null, WorkflowCompareOperator.IS_NOT_EMPTY));
        }

        @Test
        @DisplayName("IS_NULL 应正确判断 null")
        void isNullShouldWork() {
            assertTrue(CompareUtils.compare(null, null, WorkflowCompareOperator.IS_NULL));
            assertFalse(CompareUtils.compare("hello", null, WorkflowCompareOperator.IS_NULL));
        }

        @Test
        @DisplayName("IS_NOT_NULL 应正确判断非 null")
        void isNotNullShouldWork() {
            assertFalse(CompareUtils.compare(null, null, WorkflowCompareOperator.IS_NOT_NULL));
            assertTrue(CompareUtils.compare("hello", null, WorkflowCompareOperator.IS_NOT_NULL));
        }
    }

    @Nested
    @DisplayName("集合运算符测试")
    class CollectionOperatorTests {

        @Test
        @DisplayName("IN 应正确判断元素是否在列表中")
        void inListShouldWork() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertTrue(CompareUtils.compare("a", list, WorkflowCompareOperator.IN));
            assertFalse(CompareUtils.compare("d", list, WorkflowCompareOperator.IN));
        }

        @Test
        @DisplayName("IN 应正确判断元素是否在逗号分隔字符串中")
        void inStringShouldWork() {
            assertTrue(CompareUtils.compare("a", "a,b,c", WorkflowCompareOperator.IN));
            assertFalse(CompareUtils.compare("d", "a,b,c", WorkflowCompareOperator.IN));
        }

        @Test
        @DisplayName("NOT_IN 应正确判断元素不在集合中")
        void notInShouldWork() {
            List<String> list = Arrays.asList("a", "b", "c");
            assertFalse(CompareUtils.compare("a", list, WorkflowCompareOperator.NOT_IN));
            assertTrue(CompareUtils.compare("d", list, WorkflowCompareOperator.NOT_IN));
        }
    }
}
