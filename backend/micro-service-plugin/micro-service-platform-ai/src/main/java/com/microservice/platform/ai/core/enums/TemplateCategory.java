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
 * 工作流模板分类枚举
 *
 * @author xJh
 * @since 2026/01/06
 */
@Getter
@AllArgsConstructor
public enum TemplateCategory implements AiEnum {

    /**
     * RAG问答
     */
    RAG("rag", "RAG问答"),

    /**
     * 文档摘要
     */
    SUMMARY("summary", "文档摘要"),

    /**
     * 数据提取
     */
    EXTRACTION("extraction", "数据提取"),

    /**
     * 多轮对话
     */
    CONVERSATION("conversation", "多轮对话"),

    /**
     * 内容生成
     */
    GENERATION("generation", "内容生成");

    @EnumValue
    private final String code;
    private final String description;

    @JsonCreator
    public static TemplateCategory fromCode(String code) {
        return AiEnumUtils.requireCode(TemplateCategory.class, code);
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }
}
