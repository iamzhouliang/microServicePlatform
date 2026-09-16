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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 迭代节点配置
 * 对数组元素进行批量处理，支持顺序和并行模式
 * 内置变量:
 * - item: 当前迭代元素
 * - index: 当前索引
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IterationNodeConfig {

    /**
     * 要迭代的数组变量
     * 支持变量引用格式: {{nodeName.variableName}}
     */
    private String arrayVariable;

    /**
     * 处理模式
     */
    private ProcessingMode processingMode;

    /**
     * 并行数量（并行模式时有效）
     * 默认为 CPU 核心数
     */
    private Integer parallelCount;

    /**
     * 单次迭代超时时间（毫秒）
     */
    private Long iterationTimeout;

    /**
     * 最大迭代次数限制
     * 防止无限循环，默认 1000
     */
    private Integer maxIterations;

    /**
     * 输出变量名
     * 存储迭代结果数组
     */
    private String outputVariable;

    /**
     * 处理模式枚举
     */
    @Getter
    @AllArgsConstructor
    public enum ProcessingMode implements AiEnum {

        /**
         * 顺序处理
         * 按顺序逐个处理数组元素
         */
        SEQUENTIAL("SEQUENTIAL", "顺序处理"),

        /**
         * 并行处理
         * 同时处理多个数组元素
         */
        PARALLEL("PARALLEL", "并行处理");

        private final String code;
        private final String description;
    }

}
