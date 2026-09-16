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

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.ai.domain.dto.req.HarnessOperationDecisionReq;
import com.microservice.platform.ai.domain.dto.resp.HarnessOperationResp;
import com.microservice.platform.ai.service.HarnessOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Harness Operation 专用控制面，不作为聊天入口。
 *
 * @author xJh
 * @since 2026-07-18
 */
@SaCheckLogin
@RestController
@RequiredArgsConstructor
@RequestMapping("/agent-operations")
@Tag(name = "AI Operation 审计", description = "受治理 Tool Operation 的后台控制面")
public class HarnessOperationController {

    private final HarnessOperationService service;

    @GetMapping("/{operationId}")
    @SaCheckPermission("ai:agent-operation:view")
    @Operation(summary = "查询 Operation 审计快照")
    public HarnessOperationResp get(@PathVariable String operationId) {
        return service.get(operationId);
    }

    @PostMapping("/{operationId}/decision")
    @SaCheckPermission("ai:agent-operation:approve")
    @AccessLog(module = "AI Operation", description = "审批受治理 Tool Operation")
    @Operation(summary = "独立审批 Operation")
    public HarnessOperationResp decide(@PathVariable String operationId,
                                       @Valid @RequestBody HarnessOperationDecisionReq request) {
        return service.decide(operationId, request);
    }

    @PostMapping("/{operationId}/reconcile")
    @SaCheckPermission("ai:agent-operation:reconcile")
    @AccessLog(module = "AI Operation", description = "人工对账受治理 Tool Operation")
    @Operation(summary = "对结果未知的 Operation 发起权威对账")
    public HarnessOperationResp reconcile(@PathVariable String operationId) {
        return service.reconcile(operationId);
    }
}
