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

import java.time.Instant;
import java.util.Map;

/**
 * AI工作流执行记录实体
 *
 * @author xJh
 * @since 2026/01/06
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_workflow_execution", autoResultMap = true)
@Schema(name = "WorkflowExecution", description = "AI工作流执行记录")
public class WorkflowExecution extends SuperEntity<Long> {

    @Schema(description = "执行ID(UUID)")
    private String executionId;

    @Schema(description = "工作流ID")
    private Long workflowId;

    @Schema(description = "执行时的工作流版本")
    private Integer workflowVersion;

    @Schema(description = "执行状态: PENDING-等待中, RUNNING-执行中, COMPLETED-已完成, FAILED-失败, PAUSED-已暂停, CANCELLED-已取消")
    private String status;

    @Schema(description = "输入参数")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> inputs;

    @Schema(description = "输出结果")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> outputs;

    @Schema(description = "执行快照(用于断点续传)")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshot;

    @Schema(description = "节点执行状态(各节点的执行详情)")
    @TableField(typeHandler = JacksonTypeHandler.class)
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

    @Schema(description = "执行用户ID")
    private Long userId;

    @Schema(description = "租户ID")
    private Long tenantId;
}
