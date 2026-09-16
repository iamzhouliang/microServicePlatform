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

package com.microservice.platform.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.platform.ai.domain.dto.req.WorkflowExecutionPageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowExecutionReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowExecutionResp;
import com.microservice.platform.ai.service.WorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 工作流执行控制器
 * 提供工作流的执行触发、SSE 订阅和调试控制功能
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow-executions")
@Tag(name = "工作流执行", description = "工作流执行管理")
public class WorkflowExecutionController {

    private final WorkflowExecutionService workflowExecutionService;

    // ==================== 执行触发 API ====================

    @PostMapping("/workflows/{workflowId}/execute")
    @Operation(summary = "同步执行", description = "同步执行工作流，等待执行完成后返回结果")
    public WorkflowExecutionResp execute(
                                         @Parameter(description = "工作流ID") @PathVariable Long workflowId,
                                         @Validated @RequestBody WorkflowExecutionReq req) {
        if (req.getBreakpoints() != null && !req.getBreakpoints().isEmpty()) {
            return workflowExecutionService.execute(workflowId, req.getInputs(), req.getBreakpoints());
        }
        return workflowExecutionService.execute(workflowId, req.getInputs());
    }

    @PostMapping("/workflows/{workflowId}/execute-async")
    @Operation(summary = "异步执行", description = "异步执行工作流，立即返回执行ID，通过 SSE 订阅获取执行状态")
    public String executeAsync(
                               @Parameter(description = "工作流ID") @PathVariable Long workflowId,
                               @Validated @RequestBody WorkflowExecutionReq req) {
        if (req.getBreakpoints() != null && !req.getBreakpoints().isEmpty()) {
            return workflowExecutionService.executeAsync(workflowId, req.getInputs(), req.getBreakpoints());
        }
        return workflowExecutionService.executeAsync(workflowId, req.getInputs());
    }

    // ==================== SSE 订阅端点 ====================

    @GetMapping(value = "/{executionId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅执行事件", description = "通过 SSE 订阅工作流执行事件，实时获取执行状态")
    public SseEmitter subscribe(@Parameter(description = "执行ID") @PathVariable String executionId) {
        return workflowExecutionService.subscribe(executionId);
    }

    // ==================== 执行控制 API ====================

    @PostMapping("/{executionId}/pause")
    @Operation(summary = "暂停执行", description = "暂停正在执行的工作流")
    public void pause(@Parameter(description = "执行ID") @PathVariable String executionId) {
        workflowExecutionService.pause(executionId);
    }

    @PostMapping("/{executionId}/resume")
    @Operation(summary = "恢复执行", description = "恢复已暂停的工作流执行")
    public void resume(@Parameter(description = "执行ID") @PathVariable String executionId) {
        workflowExecutionService.resume(executionId);
    }

    @PostMapping("/{executionId}/cancel")
    @Operation(summary = "取消执行", description = "取消正在执行的工作流")
    public void cancel(@Parameter(description = "执行ID") @PathVariable String executionId) {
        workflowExecutionService.cancel(executionId);
    }

    @PutMapping("/{executionId}/variables/{variableName}")
    @Operation(summary = "更新变量", description = "调试模式下更新执行上下文中的变量值")
    public void updateVariable(
                               @Parameter(description = "执行ID") @PathVariable String executionId,
                               @Parameter(description = "变量名") @PathVariable String variableName,
                               @RequestBody Object value) {
        workflowExecutionService.updateVariable(executionId, variableName, value);
    }

    @GetMapping("/{executionId}/snapshot")
    @Operation(summary = "获取快照", description = "获取执行快照，用于断点续传")
    public Map<String, Object> getSnapshot(@Parameter(description = "执行ID") @PathVariable String executionId) {
        return workflowExecutionService.getSnapshot(executionId);
    }

    @PostMapping("/{executionId}/resume-from-snapshot")
    @Operation(summary = "从快照恢复", description = "从快照恢复执行")
    public WorkflowExecutionResp resumeFromSnapshot(
                                                    @Parameter(description = "执行ID") @PathVariable String executionId,
                                                    @RequestBody Map<String, Object> snapshot) {
        return workflowExecutionService.resumeFromSnapshot(executionId, snapshot);
    }

    // ==================== 查询 API ====================

    @GetMapping("/{executionId}")
    @Operation(summary = "执行详情", description = "获取工作流执行详情")
    public WorkflowExecutionResp getExecution(@Parameter(description = "执行ID") @PathVariable String executionId) {
        return workflowExecutionService.getExecution(executionId);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询工作流执行历史")
    public IPage<WorkflowExecutionResp> pageExecutions(WorkflowExecutionPageReq req) {
        return workflowExecutionService.pageExecutions(req);
    }

    @GetMapping("/workflows/{workflowId}/executions")
    @Operation(summary = "工作流执行历史", description = "获取指定工作流的执行历史")
    public IPage<WorkflowExecutionResp> getWorkflowExecutions(
                                                              @Parameter(description = "工作流ID") @PathVariable Long workflowId,
                                                              WorkflowExecutionPageReq req) {
        req.setWorkflowId(workflowId);
        return workflowExecutionService.pageExecutions(req);
    }
}
