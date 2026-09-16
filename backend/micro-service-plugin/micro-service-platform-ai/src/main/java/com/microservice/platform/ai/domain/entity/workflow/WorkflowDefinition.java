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

package com.microservice.platform.ai.domain.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.commons.entity.SuperEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * AI工作流定义实体
 *
 * @author xJh
 * @since 2026/01/06
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_workflow", autoResultMap = true)
@Schema(name = "WorkflowDefinition", description = "AI工作流定义")
public class WorkflowDefinition extends SuperEntity<Long> {

    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "工作流图定义(JSON格式，包含nodes和edges)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private WorkflowGraph graph;

    @Schema(description = "输入变量定义")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> inputVariables;

    @Schema(description = "输出变量定义")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<Map<String, Object>> outputVariables;

    @Schema(description = "当前版本号")
    private Integer currentVersion;

    @Schema(description = "状态: DRAFT-草稿, PUBLISHED-已发布, ARCHIVED-已归档")
    private String status;

    @Schema(description = "所属用户ID")
    private Long userId;

    @Schema(description = "租户ID")
    private Long tenantId;
}
