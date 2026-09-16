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

/**
 * AI工作流模板实体
 *
 * @author xJh
 * @since 2026/01/06
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_workflow_template", autoResultMap = true)
@Schema(name = "WorkflowTemplate", description = "AI工作流模板")
public class WorkflowTemplate extends SuperEntity<Long> {

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "模板分类: rag-RAG问答, summary-文档摘要, extraction-数据提取, conversation-多轮对话, generation-内容生成")
    private String category;

    @Schema(description = "模板图标")
    private String icon;

    @Schema(description = "工作流图定义")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private WorkflowGraph graph;

    @Schema(description = "是否内置模板")
    private Boolean builtIn;

    @Schema(description = "创建用户ID(自定义模板)")
    private Long userId;

    @Schema(description = "租户ID")
    private Long tenantId;
}
