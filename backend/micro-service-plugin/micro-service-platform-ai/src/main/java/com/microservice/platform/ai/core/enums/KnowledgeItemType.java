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
import com.microservice.framework.commons.entity.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 知识条目类型枚举
 * 定义知识库中支持的条目类型，不同类型有不同的处理逻辑。
 * </p>
 * <h3>类型说明</h3>
 * <ul>
 *   <li><b>DOCUMENT</b>：文档类型，如 PDF、Word、Markdown 等</li>
 *   <li><b>QA_PAIR</b>：问答对，包含问题和答案</li>
 *   <li><b>STRUCTURED</b>：结构化数据，如表格、JSON 等</li>
 *   <li><b>TEXT_SNIPPET</b>：文本片段，手动输入的短文本</li>
 * </ul>
 *
 * @author Levin
 * @since 2025/10/11
 */
@Getter
@AllArgsConstructor
public enum KnowledgeItemType implements DictEnum<String>, AiEnum {

    /**
     * 文档类型
     * 支持 PDF、Word、Markdown、TXT 等文档格式。
     * </p>
     */
    DOCUMENT("DOCUMENT", "文档"),

    /**
     * 问答对
     * 包含问题和答案的结构化知识。
     * </p>
     */
    QA_PAIR("QA_PAIR", "问答对"),

    /**
     * 结构化数据
     * 表格、JSON 等结构化数据。
     * </p>
     */
    STRUCTURED("STRUCTURED", "结构化数据"),

    /**
     * 文本片段
     * 手动输入的短文本内容。
     * </p>
     */
    TEXT_SNIPPET("TEXT_SNIPPET", "文本片段");

    /**
     * 类型编码（用于数据库存储）
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 类型标签
     */
    private final String label;

    /**
     * 根据编码获取类型
     *
     * @param code 类型编码
     * @return 对应的类型，未找到返回 null
     */
    public static KnowledgeItemType of(String code) {
        return fromCode(code);
    }

    @JsonCreator
    public static KnowledgeItemType fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (KnowledgeItemType type : values()) {
            if (type.value.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String getCode() {
        return value;
    }

    @Override
    public String getDescription() {
        return label;
    }

    /**
     * 是否需要文件解析
     *
     * @return 是否需要文件解析
     */
    public boolean requiresFileParsing() {
        return this == DOCUMENT;
    }
}
