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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 问题分类器节点配置
 * 使用 LLM 对问题进行智能分类，路由到不同的处理分支
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class QuestionClassifierConfig {

    /**
     * 分类使用的模型 ID
     */
    private Long modelId;

    /**
     * 输入变量（要分类的文本）
     * 支持变量引用格式: {{nodeName.variableName}}
     */
    private String inputVariable;

    /**
     * 分类指导说明
     * 用于指导 LLM 如何进行分类
     */
    private String instructions;

    /**
     * 分类类别列表
     */
    private List<ClassCategory> categories;

    /**
     * 是否启用高级模式
     * 高级模式下可以自定义分类提示词
     */
    private boolean advancedMode;

    /**
     * 自定义分类提示词模板（高级模式）
     */
    private String customPromptTemplate;

    /**
     * 分类类别定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClassCategory {

        /**
         * 类别 ID（用于输出端口标识）
         */
        private String id;

        /**
         * 类别名称
         */
        private String name;

        /**
         * 类别描述（帮助 LLM 理解分类标准）
         */
        private String description;

        /**
         * 示例问题（可选，帮助 LLM 更好地理解）
         */
        private List<String> examples;
    }
}
