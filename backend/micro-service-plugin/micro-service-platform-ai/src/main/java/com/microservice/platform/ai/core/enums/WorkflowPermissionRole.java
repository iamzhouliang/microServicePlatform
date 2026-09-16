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
 * 工作流权限角色枚举
 *
 * @author xJh
 * @since 2026/01/06
 */
@Getter
@AllArgsConstructor
public enum WorkflowPermissionRole implements AiEnum {

    /**
     * 所有者
     */
    OWNER("OWNER", "所有者"),

    /**
     * 编辑者
     */
    EDITOR("EDITOR", "编辑者"),

    /**
     * 查看者
     */
    VIEWER("VIEWER", "查看者");

    @EnumValue
    private final String code;
    private final String description;

    @JsonCreator
    public static WorkflowPermissionRole fromCode(String code) {
        return AiEnumUtils.requireCode(WorkflowPermissionRole.class, code);
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    /**
     * 判断是否有编辑权限
     *
     * @return 是否有编辑权限
     */
    public boolean canEdit() {
        return this == OWNER || this == EDITOR;
    }

    /**
     * 判断是否有删除权限
     *
     * @return 是否有删除权限
     */
    public boolean canDelete() {
        return this == OWNER;
    }
}
