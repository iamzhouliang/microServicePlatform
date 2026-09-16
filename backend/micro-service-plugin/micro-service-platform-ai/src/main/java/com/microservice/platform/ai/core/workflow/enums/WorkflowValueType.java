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

package com.microservice.platform.ai.core.workflow.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.microservice.platform.ai.core.enums.AiEnum;
import com.microservice.platform.ai.core.enums.AiEnumUtils;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作流变量和值类型。
 */
@Getter
@AllArgsConstructor
public enum WorkflowValueType implements AiEnum {

    STRING("string", "字符串"),
    NUMBER("number", "数字"),
    BOOLEAN("boolean", "布尔值"),
    ARRAY("array", "数组"),
    OBJECT("object", "对象"),
    ANY("any", "任意类型");

    private final String code;
    private final String description;

    @JsonCreator
    public static WorkflowValueType fromCode(String code) {
        return AiEnumUtils.requireCode(WorkflowValueType.class, code);
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }
}
