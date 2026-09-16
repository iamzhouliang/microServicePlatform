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

package com.microservice.platform.ai.domain.dto.req;

import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 工作流模板保存请求
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Schema(description = "工作流模板保存请求")
public class WorkflowTemplateSaveReq {

    @NotBlank(message = "模板名称不能为空")
    @Schema(description = "模板名称")
    private String name;

    @Schema(description = "模板描述")
    private String description;

    @NotBlank(message = "模板分类不能为空")
    @Schema(description = "模板分类: rag-RAG问答, summary-文档摘要, extraction-数据提取, conversation-多轮对话, generation-内容生成")
    private String category;

    @Schema(description = "模板图标")
    private String icon;

    @Schema(description = "工作流图定义")
    private WorkflowGraph graph;
}
