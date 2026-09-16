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
 * 参数提取器节点配置
 * 从自然语言文本中提取结构化参数
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParameterExtractorConfig {

    /**
     * 提取使用的模型 ID
     */
    private Long modelId;

    /**
     * 输入变量（要提取参数的文本）
     * 支持变量引用格式: {{nodeName.variableName}}
     */
    private String inputVariable;

    /**
     * 提取指导说明
     */
    private String instructions;

    /**
     * 要提取的参数列表
     */
    private List<ExtractParameter> parameters;

    /**
     * 推理模式
     */
    private InferenceMode inferenceMode;

    /**
     * 是否启用记忆（对话历史）
     */
    private boolean memoryEnabled;

    /**
     * 记忆窗口大小（启用记忆时有效）
     */
    private Integer memoryWindowSize;

    /**
     * 要提取的参数定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractParameter {

        /**
         * 参数名
         */
        private String name;

        /**
         * 参数类型
         */
        private WorkflowValueType type;

        /**
         * 参数描述（帮助 LLM 理解要提取什么）
         */
        private String description;

        /**
         * 是否必填
         */
        private boolean required;

        /**
         * 枚举值（type 为 STRING 时可用于限制取值范围）
         */
        private List<String> enumValues;
    }

    /**
     * 推理模式枚举
     */
    @Getter
    @AllArgsConstructor
    public enum InferenceMode implements AiEnum {

        /**
         * 使用模型的 Function Call 能力
         * 更精确，但需要模型支持
         */
        FUNCTION_CALL("FUNCTION_CALL", "Function Call 模式"),

        /**
         * 基于提示词提取
         * 适用范围更广，适用于所有模型
         */
        PROMPT_BASED("PROMPT_BASED", "提示词模式");

        private final String code;
        private final String description;
    }
}
