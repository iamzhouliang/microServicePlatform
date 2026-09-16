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
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * IF/ELSE 条件分支节点配置
 * 支持 IF/ELIF/ELSE 多分支和 AND/OR 条件组合
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IfElseNodeConfig {

    /**
     * 条件分支列表
     * 按顺序评估，第一个满足条件的分支被执行
     */
    private List<ConditionBranch> branches;

    /**
     * 条件分支定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConditionBranch {

        /**
         * 分支 ID（用于输出端口标识）
         */
        private String id;

        /**
         * 分支标签
         */
        private String label;

        /**
         * 分支类型
         */
        private BranchType type;

        /**
         * 条件列表
         * ELSE 分支不需要条件
         */
        private List<Condition> conditions;

        /**
         * 条件组合方式
         * 多个条件之间的逻辑关系
         */
        private WorkflowLogicalOperator operator;
    }

    /**
     * 分支类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum BranchType implements AiEnum {

        /**
         * IF 分支（第一个条件分支）
         */
        IF("IF", "如果"),

        /**
         * ELIF 分支（额外的条件分支）
         */
        ELIF("ELIF", "否则如果"),

        /**
         * ELSE 分支（默认分支，无条件）
         */
        ELSE("ELSE", "否则");

        private final String code;
        private final String description;
    }

    /**
     * 条件定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Condition {

        /**
         * 变量引用
         * 支持格式: {{nodeName.variableName}}
         */
        private String variable;

        /**
         * 比较运算符
         */
        private WorkflowCompareOperator operator;

        /**
         * 比较值
         * 可以是字面量或变量引用
         */
        private Object value;

        /**
         * 值是否为变量引用
         */
        private boolean valueIsVariable;
    }

}
