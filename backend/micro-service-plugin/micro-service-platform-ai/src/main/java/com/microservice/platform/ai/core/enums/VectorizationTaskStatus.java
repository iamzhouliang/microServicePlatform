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
 * 向量化任务状态字典。
 * 取代实体中散落的 "PENDING"/"PROCESSING"/"COMPLETED"/"FAILED" 魔法字符串，统一入库编码与前端展示。
 * <h3>状态流转</h3>
 * <pre>
 * PENDING → PROCESSING → COMPLETED
 *               ↓
 *            FAILED
 * </pre>
 *
 * @author xJh
 */
@Getter
@AllArgsConstructor
public enum VectorizationTaskStatus implements AiEnum {

    /**
     * 等待中
     */
    PENDING("PENDING", "等待中"),

    /**
     * 处理中
     */
    PROCESSING("PROCESSING", "处理中"),

    /**
     * 已完成
     */
    COMPLETED("COMPLETED", "已完成"),

    /**
     * 失败
     */
    FAILED("FAILED", "失败");

    @EnumValue
    @JsonValue
    private final String code;

    private final String description;

    @JsonCreator
    public static VectorizationTaskStatus fromCode(String code) {
        return AiEnumUtils.fromCode(VectorizationTaskStatus.class, code);
    }
}
