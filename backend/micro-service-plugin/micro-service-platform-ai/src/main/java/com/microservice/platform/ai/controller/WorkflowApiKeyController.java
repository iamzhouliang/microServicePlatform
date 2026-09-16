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

import com.microservice.platform.ai.domain.entity.workflow.WorkflowApiKey;
import com.microservice.platform.ai.service.WorkflowApiKeyService;
import com.microservice.platform.ai.service.impl.WorkflowApiKeyServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流 API Key 管理控制器
 * 提供后台管理界面对 API Key 的 CRUD 操作
 *
 * @return 处理结果
 * @author xJh
 * @since 2026/02/06
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow-api-keys")
@Tag(name = "工作流 API Key 管理", description = "管理工作流的第三方访问凭证")
public class WorkflowApiKeyController {

    private final WorkflowApiKeyService workflowApiKeyService;

    /**
     * 创建 API Key
     * @param req 请求参数
     * @return 处理结果
     */
    @PostMapping
    @Operation(summary = "创建 API Key", description = "为指定工作流创建新的 API Key")
    public ApiKeyCreateResp create(@Validated @RequestBody ApiKeyCreateReq req) {
        WorkflowApiKey entity = workflowApiKeyService.createApiKey(
                req.getWorkflowId(), req.getName(), req.getRateLimit(), req.getExpireDays());

        // 创建时返回完整 key（仅此一次）
        ApiKeyCreateResp resp = new ApiKeyCreateResp();
        resp.setId(entity.getId());
        resp.setApiKey(entity.getApiKey());
        resp.setName(entity.getName());
        resp.setWorkflowId(entity.getWorkflowId());
        return resp;
    }

    /**
     * 查询工作流的 API Key 列表
     * @param workflowId 工作流标识
     * @return 处理结果
     */
    @GetMapping("/workflows/{workflowId}")
    @Operation(summary = "查询 API Key 列表", description = "查询指定工作流的所有 API Key")
    public List<ApiKeyListResp> list(
                                     @Parameter(description = "工作流ID") @PathVariable Long workflowId) {
        return workflowApiKeyService.listByWorkflowId(workflowId).stream()
                .map(entity -> {
                    ApiKeyListResp resp = new ApiKeyListResp();
                    resp.setId(entity.getId());
                    resp.setName(entity.getName());
                    resp.setApiKeyMasked(WorkflowApiKeyServiceImpl.maskApiKey(entity.getApiKey()));
                    resp.setStatus(entity.getStatus() == null ? null : entity.getStatus().getCode());
                    resp.setRateLimit(entity.getRateLimit());
                    resp.setExpireTime(entity.getExpireTime() != null ? entity.getExpireTime().toString() : null);
                    resp.setLastUsedTime(entity.getLastUsedTime() != null ? entity.getLastUsedTime().toString() : null);
                    resp.setTotalCalls(entity.getTotalCalls());
                    resp.setCreateTime(entity.getCreateTime() != null ? entity.getCreateTime().toString() : null);
                    return resp;
                })
                .toList();
    }

    /**
     * 更新 API Key 状态
     * @param status 状态
     * @param id 主键标识
     */
    @PutMapping("/{id}/status")
    @Operation(summary = "启用/禁用 API Key")
    public void updateStatus(
                             @Parameter(description = "API Key ID") @PathVariable Long id,
                             @RequestParam String status) {
        workflowApiKeyService.updateStatus(id, status);
    }

    /**
     * 删除 API Key
     * @param id 主键标识
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除 API Key")
    public void delete(@Parameter(description = "API Key ID") @PathVariable Long id) {
        workflowApiKeyService.deleteApiKey(id);
    }

    // ==================== 请求/响应 DTO ====================

    @Data
    public static class ApiKeyCreateReq {

        @Parameter(description = "工作流ID", required = true)
        private Long workflowId;

        @Parameter(description = "备注名称", required = true)
        private String name;

        @Parameter(description = "每秒请求限制（QPS），0 或 null 表示不限制")
        private Integer rateLimit;

        @Parameter(description = "过期天数，null 表示永不过期")
        private Integer expireDays;
    }

    @Data
    public static class ApiKeyCreateResp {

        private Long id;
        /** 完整的 API Key（仅创建时返回一次） */
        private String apiKey;
        private String name;
        private Long workflowId;
    }

    @Data
    public static class ApiKeyListResp {

        private Long id;
        private String name;
        /** 脱敏后的 API Key */
        private String apiKeyMasked;
        private String status;
        private Integer rateLimit;
        private String expireTime;
        private String lastUsedTime;
        private Long totalCalls;
        private String createTime;
    }
}
