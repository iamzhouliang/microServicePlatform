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
 * 工作流版本响应
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Schema(description = "工作流版本响应")
public class WorkflowVersionResp {

    @Schema(description = "版本ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "工作流ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "工作流图快照")
    private WorkflowGraph graphSnapshot;

    @Schema(description = "变更说明")
    private String changeLog;

    @Schema(description = "是否已发布")
    private Boolean published;

    @Schema(description = "创建人ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createdBy;

    @Schema(description = "创建时间")
    private Instant createdTime;
}
