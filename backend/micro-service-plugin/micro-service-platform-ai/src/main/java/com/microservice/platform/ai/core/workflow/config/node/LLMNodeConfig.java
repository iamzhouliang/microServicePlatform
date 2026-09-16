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
 * LLM 大模型节点配置
 * 调用 LLM 进行推理，支持 Vision、Memory、结构化输出
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LLMNodeConfig {

    /**
     * 模型 ID
     */
    private Long modelId;

    /**
     * 系统提示词
     */
    private String systemPrompt;

    /**
     * 用户提示词模板
     * 支持变量引用: {{nodeName.variableName}}
     */
    private String promptTemplate;

    /**
     * 温度参数 (0-2)
     * 控制输出的随机性
     */
    private Double temperature;

    /**
     * 最大 Token 数
     */
    private Integer maxTokens;

    /**
     * 是否流式输出
     */
    private boolean streaming;

    /**
     * 输出变量名
     */
    private String outputVariable;

    // ==================== Dify 增强功能 ====================

    /**
     * Vision 开关（图像理解）
     * 启用后可以处理图像输入
     */
    private boolean visionEnabled;

    /**
     * 图像变量列表（Vision 启用时有效）
     * 支持变量引用: {{nodeName.imageVariable}}
     */
    private List<String> imageVariables;

    /**
     * Memory 开关（对话记忆）
     * 启用后会保留对话历史
     */
    private boolean memoryEnabled;

    /**
     * 记忆窗口大小
     * 保留最近 N 轮对话
     */
    private Integer memoryWindowSize;

    /**
     * 结构化输出配置
     */
    private StructuredOutput structuredOutput;

    /**
     * 上下文变量列表
     * 用于在提示词中引用的变量
     */
    private List<ContextVariable> contextVariables;

    /**
     * 结构化输出配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StructuredOutput {

        /**
         * 是否启用结构化输出
         */
        private boolean enabled;

        /**
         * JSON Schema 定义
         * 定义输出的结构
         */
        private String jsonSchema;

        /**
         * 输出描述
         * 帮助 LLM 理解期望的输出格式
         */
        private String description;

        /**
         * 是否严格模式
         * 严格模式下输出必须完全符合 Schema
         */
        private boolean strictMode;
    }

    /**
     * 上下文变量定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContextVariable {

        /**
         * 变量名（在提示词中使用）
         */
        private String name;

        /**
         * 变量引用
         * 支持格式: {{nodeName.variableName}}
         */
        private String reference;

        /**
         * 变量描述
         */
        private String description;
    }
}
