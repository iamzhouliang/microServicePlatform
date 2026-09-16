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

import com.microservice.framework.db.mybatisplus.page.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * 工作流执行分页查询请求
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "WorkflowExecutionPageReq", description = "工作流执行分页查询请求")
public class WorkflowExecutionPageReq extends PageRequest {

    @Schema(description = "工作流ID")
    private Long workflowId;

    @Schema(description = "执行状态: PENDING, RUNNING, COMPLETED, FAILED, PAUSED, CANCELLED")
    private String status;

    @Schema(description = "开始时间（起）")
    private Instant startTimeFrom;

    @Schema(description = "开始时间（止）")
    private Instant startTimeTo;
}
