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
 * 工作流通用比较运算符。
 */
@Getter
@AllArgsConstructor
public enum WorkflowCompareOperator implements AiEnum {

    EQUALS("EQUALS", "等于", "=="),
    NOT_EQUALS("NOT_EQUALS", "不等于", "!="),
    GREATER_THAN("GREATER_THAN", "大于", ">"),
    LESS_THAN("LESS_THAN", "小于", "<"),
    GREATER_OR_EQUAL("GREATER_OR_EQUAL", "大于等于", ">="),
    LESS_OR_EQUAL("LESS_OR_EQUAL", "小于等于", "<="),
    CONTAINS("CONTAINS", "包含", "contains"),
    NOT_CONTAINS("NOT_CONTAINS", "不包含", "not contains"),
    STARTS_WITH("STARTS_WITH", "开头是", "starts with"),
    ENDS_WITH("ENDS_WITH", "结尾是", "ends with"),
    MATCHES_REGEX("MATCHES_REGEX", "正则匹配", "matches"),
    MATCHES("MATCHES", "正则匹配", "matches"),
    IS_EMPTY("IS_EMPTY", "为空", "is empty"),
    IS_NOT_EMPTY("IS_NOT_EMPTY", "不为空", "is not empty"),
    IS_NULL("IS_NULL", "为 null", "is null"),
    IS_NOT_NULL("IS_NOT_NULL", "不为 null", "is not null"),
    IN("IN", "在列表中", "in"),
    NOT_IN("NOT_IN", "不在列表中", "not in");

    private final String code;
    private final String description;
    private final String symbol;

    @JsonCreator
    public static WorkflowCompareOperator fromCode(String code) {
        return AiEnumUtils.requireCode(WorkflowCompareOperator.class, code);
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }
}
