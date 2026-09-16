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

import cn.dev33.satoken.annotation.SaIgnore;
import com.microservice.framework.commons.entity.Result;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.platform.ai.core.openapi.ApiKeyAuthInterceptor;
import com.microservice.platform.ai.domain.dto.resp.WorkflowExecutionResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowApiKey;
import com.microservice.platform.ai.service.WorkflowExecutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 开放工作流 API
 * 提供给第三方系统通过 API Key 调用的工作流执行接口。
 * 所有接口通过 {@link ApiKeyAuthInterceptor} 进行 API Key 认证，
 * 无需平台登录态。
 * 认证方式（任选其一）：
 * <ul>
 *   <li>请求头: Authorization: Bearer sk-wf-xxx</li>
 *   <li>请求头: X-Api-Key: sk-wf-xxx</li>
 *   <li>URL参数: ?api_key=sk-wf-xxx</li>
 * </ul>
 *
 * @author xJh
 * @since 2026/02/06
 */
@Slf4j
@SaIgnore
@RestController
@RequiredArgsConstructor
@RequestMapping("/open-api/workflows")
@Tag(name = "开放API - 工作流", description = "第三方系统通过 API Key 调用工作流")
public class OpenWorkflowController {

    private final WorkflowExecutionService workflowExecutionService;

    /**
     * 同步执行工作流
     * 阻塞等待工作流执行完成后返回结果，适用于执行时间较短的工作流。
     *
     * @param inputs  输入参数
     * @param request HTTP 请求（用于获取认证信息）
     * @return 执行结果
     */
    @PostMapping("/run")
    @Operation(summary = "同步执行工作流", description = "通过 API Key 同步执行关联的工作流，等待完成后返回结果")
    public Result<WorkflowExecutionResp> run(
                                             @RequestBody(required = false) Map<String, Object> inputs,
                                             HttpServletRequest request) {
        WorkflowApiKey apiKey = getAuthenticatedApiKey(request);
        WorkflowExecutionResp resp = workflowExecutionService.execute(apiKey.getWorkflowId(),
                inputs != null ? inputs : Map.of());
        return Result.success(resp);
    }

    /**
     * 异步执行工作流
     * 立即返回执行ID，第三方可通过 SSE 订阅或轮询获取执行结果。
     *
     * @param inputs  输入参数
     * @param request HTTP 请求
     * @return 执行ID
     */
    @PostMapping("/run-async")
    @Operation(summary = "异步执行工作流", description = "通过 API Key 异步执行关联的工作流，立即返回执行ID")
    public Result<String> runAsync(
                                   @RequestBody(required = false) Map<String, Object> inputs,
                                   HttpServletRequest request) {
        WorkflowApiKey apiKey = getAuthenticatedApiKey(request);
        String executionId = workflowExecutionService.executeAsync(apiKey.getWorkflowId(),
                inputs != null ? inputs : Map.of());
        return Result.success(executionId);
    }

    /**
     * 订阅执行事件（SSE）
     * 通过 Server-Sent Events 实时获取工作流执行状态和节点输出。
     *
     * @param executionId 执行ID
      * @param request 请求参数
     * @return SSE 事件流
     */
    @GetMapping(value = "/executions/{executionId}/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "订阅执行事件", description = "通过 SSE 实时获取工作流执行状态")
    public SseEmitter subscribe(@PathVariable String executionId, HttpServletRequest request) {
        // 验证 API Key 已认证
        getAuthenticatedApiKey(request);
        return workflowExecutionService.subscribe(executionId);
    }

    /**
     * 查询执行结果
     *
     * @param executionId 执行ID
      * @param request 请求参数
     * @return 执行详情
     */
    @GetMapping("/executions/{executionId}")
    @Operation(summary = "查询执行结果", description = "查询工作流执行详情和结果")
    public Result<WorkflowExecutionResp> getExecution(@PathVariable String executionId, HttpServletRequest request) {
        // 验证 API Key 已认证
        getAuthenticatedApiKey(request);
        WorkflowExecutionResp resp = workflowExecutionService.getExecution(executionId);
        return Result.success(resp);
    }

    /**
     * 取消执行
     *
     * @param executionId 执行ID
      * @param request 请求参数
     * @return 处理结果
     */
    @PostMapping("/executions/{executionId}/cancel")
    @Operation(summary = "取消执行", description = "取消正在执行的工作流")
    public Result<Void> cancel(@PathVariable String executionId, HttpServletRequest request) {
        getAuthenticatedApiKey(request);
        workflowExecutionService.cancel(executionId);
        return Result.success();
    }

    /**
     * 从 request attribute 中获取已认证的 API Key
      * @param request 请求参数
      * @return 处理结果
     */
    private WorkflowApiKey getAuthenticatedApiKey(HttpServletRequest request) {
        Object attr = request.getAttribute(ApiKeyAuthInterceptor.ATTR_API_KEY);
        if (attr instanceof WorkflowApiKey apiKey) {
            return apiKey;
        }
        throw CheckedException.badRequest("API Key authentication required");
    }
}
