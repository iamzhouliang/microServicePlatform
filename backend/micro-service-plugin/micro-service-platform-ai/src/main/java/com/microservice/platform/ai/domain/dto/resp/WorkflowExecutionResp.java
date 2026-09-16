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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 工作流执行响应
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WorkflowExecutionResp", description = "工作流执行响应")
public class WorkflowExecutionResp {

    @Schema(description = "执行记录ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "执行ID(UUID)")
    private String executionId;

    @Schema(description = "工作流ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long workflowId;

    @Schema(description = "工作流名称")
    private String workflowName;

    @Schema(description = "执行时的工作流版本")
    private Integer workflowVersion;

    @Schema(description = "执行状态: PENDING, RUNNING, COMPLETED, FAILED, PAUSED, CANCELLED")
    private String status;

    @Schema(description = "输入参数")
    private Map<String, Object> inputs;

    @Schema(description = "输出结果")
    private Map<String, Object> outputs;

    @Schema(description = "节点执行状态(各节点的执行详情)")
    private Map<String, Object> nodeStates;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "开始时间")
    private Instant startTime;

    @Schema(description = "结束时间")
    private Instant endTime;

    @Schema(description = "执行耗时(毫秒)")
    private Long duration;

    @Schema(description = "输入Token数")
    private Long inputTokens;

    @Schema(description = "输出Token数")
    private Long outputTokens;

    @Schema(description = "总Token数")
    private Long totalTokens;

    @Schema(description = "LLM调用次数")
    private Integer llmCallCount;

    @Schema(description = "已执行的节点ID列表")
    private List<String> executedNodes;

    @Schema(description = "当前节点ID（暂停时）")
    private String currentNodeId;

    @Schema(description = "执行用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "创建时间")
    private Instant createdTime;
}
