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
 * 消息角色枚举
 * 定义对话中消息的发送者角色，用于区分不同来源的消息。
 * </p>
 *
 * @author Levin
 * @since 2025/10/11
 */
@Getter
@AllArgsConstructor
public enum MessageRole implements DictEnum<String>, AiEnum {

    /**
     * 系统消息
     * 用于设置 AI 的行为和角色定义。
     * </p>
     */
    SYSTEM("SYSTEM", "系统"),

    /**
     * 用户消息
     * 用户发送的消息。
     * </p>
     */
    USER("USER", "用户"),

    /**
     * AI 助手消息
     * AI 生成的回复消息。
     * </p>
     */
    ASSISTANT("ASSISTANT", "助手"),

    /**
     * 工具消息
     * 工具调用的结果消息。
     * </p>
     */
    TOOL("TOOL", "工具");

    /**
     * 角色编码（用于数据库存储）
     */
    @EnumValue
    @JsonValue
    private final String value;

    /**
     * 角色标签
     */
    private final String label;

    /**
     * 根据编码获取角色
     *
     * @param code 角色编码
     * @return 对应的角色，未找到返回 null
     */
    public static MessageRole of(String code) {
        return fromCode(code);
    }

    @JsonCreator
    public static MessageRole fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (MessageRole role : values()) {
            if (role.value.equalsIgnoreCase(code)) {
                return role;
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
}
