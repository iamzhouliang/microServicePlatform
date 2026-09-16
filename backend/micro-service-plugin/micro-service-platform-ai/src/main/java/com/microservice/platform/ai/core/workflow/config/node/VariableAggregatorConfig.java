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
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 变量聚合器节点配置
 * 合并多分支输出变量
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VariableAggregatorConfig {

    /**
     * 聚合组列表
     */
    private List<AggregationGroup> groups;

    /**
     * 聚合组定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AggregationGroup {

        /**
         * 输出变量名
         */
        private String outputVariable;

        /**
         * 源变量列表（来自不同分支）
         * 支持变量引用格式: {{nodeName.variableName}}
         */
        private List<String> sourceVariables;

        /**
         * 变量类型约束
         * 所有源变量必须具有相同的数据类型
         */
        private WorkflowValueType variableType;

        /**
         * 聚合策略
         */
        private AggregationStrategy strategy;
    }

    /**
     * 聚合策略枚举
     */
    @Getter
    @AllArgsConstructor
    public enum AggregationStrategy implements AiEnum {

        /**
         * 取第一个非空值
         */
        FIRST_NON_NULL("FIRST_NON_NULL", "取第一个非空值"),

        /**
         * 取最后一个非空值
         */
        LAST_NON_NULL("LAST_NON_NULL", "取最后一个非空值"),

        /**
         * 合并为数组
         */
        MERGE_TO_ARRAY("MERGE_TO_ARRAY", "合并为数组"),

        /**
         * 合并对象（仅适用于 OBJECT 类型）
         */
        MERGE_OBJECTS("MERGE_OBJECTS", "合并对象");

        private final String code;
        private final String description;
    }
}
