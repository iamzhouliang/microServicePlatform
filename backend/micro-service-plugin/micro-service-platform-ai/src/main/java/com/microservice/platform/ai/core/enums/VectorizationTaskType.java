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

package com.microservice.platform.ai.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 向量化任务类型字典。
 * 取代 VectorServiceImpl 中散落的 "KNOWLEDGE_ITEM"/"BATCH"/"DOCUMENT"/"FAQ"/"STRUCTURED" 魔法字符串。
 *
 * @author xJh
 */
@Getter
@AllArgsConstructor
public enum VectorizationTaskType implements AiEnum {

    /**
     * 单个知识条目向量化
     */
    KNOWLEDGE_ITEM("KNOWLEDGE_ITEM", "知识条目"),

    /**
     * 批量知识条目向量化
     */
    BATCH("BATCH", "批量条目"),

    /**
     * 文档向量化
     */
    DOCUMENT("DOCUMENT", "文档"),

    /**
     * FAQ 向量化
     */
    FAQ("FAQ", "FAQ 问答"),

    /**
     * 结构化数据向量化
     */
    STRUCTURED("STRUCTURED", "结构化数据");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    @JsonCreator
    public static VectorizationTaskType fromCode(String code) {
        return AiEnumUtils.fromCode(VectorizationTaskType.class, code);
    }
}
