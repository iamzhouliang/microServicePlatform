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

import java.util.List;
import java.util.Map;

/**
 * 工作流保存请求
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Schema(description = "工作流保存请求")
public class WorkflowSaveReq {

    @NotBlank(message = "工作流名称不能为空")
    @Schema(description = "工作流名称")
    private String name;

    @Schema(description = "工作流描述")
    private String description;

    @Schema(description = "工作流图定义")
    private WorkflowGraph graph;

    @Schema(description = "输入变量定义")
    private List<Map<String, Object>> inputVariables;

    @Schema(description = "输出变量定义")
    private List<Map<String, Object>> outputVariables;

    @Schema(description = "变更说明（用于版本记录）")
    private String changeLog;
}
