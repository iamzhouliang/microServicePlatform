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

package com.microservice.platform.ai.domain.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;

/**
 * 工作流模板响应
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Schema(description = "工作流模板响应")
public class WorkflowTemplateResp {

    @Schema(description = "模板ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @Schema(description = "模板分类")
    private String category;

    @Schema(description = "模板分类描述")
    private String categoryDesc;

    @Schema(description = "模板图标")
    private String icon;

    @Schema(description = "工作流图定义")
    private WorkflowGraph graph;

    @Schema(description = "是否内置模板")
    private Boolean builtIn;

    @Schema(description = "节点数量")
    private Integer nodeCount;

    @Schema(description = "创建时间")
    private Instant createTime;
}
