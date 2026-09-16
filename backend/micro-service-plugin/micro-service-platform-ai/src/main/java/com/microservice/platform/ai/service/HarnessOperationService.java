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

package com.microservice.platform.ai.service;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.platform.ai.domain.dto.req.HarnessOperationDecisionReq;
import com.microservice.platform.ai.domain.dto.resp.HarnessOperationResp;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;

/**
 * Harness Operation 后台审计、审批和人工对账服务。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Service
@RequiredArgsConstructor
public class HarnessOperationService {

    private final AuthenticationContext authentication;
    private final HarnessOperationStore store;
    private final PendingActionService pendingActionService;
    private final HarnessOperationCoordinator coordinator;
    private final ToolsetResolver toolsetResolver;
    private final Environment environment;

    public HarnessOperationResp get(String operationId) {
        return HarnessOperationResp.from(required(operationId));
    }

    public HarnessOperationResp decide(String operationId, HarnessOperationDecisionReq request) {
        HarnessOperation operation = required(operationId);
        HarnessInvocation approver = invocation(operation, operationId);
        HarnessOperation decided = Boolean.TRUE.equals(request.getApproved())
                ? pendingActionService.approve(approver, operationId)
                : pendingActionService.reject(approver, operationId, request.getReason());
        return HarnessOperationResp.from(decided);
    }

    public HarnessOperationResp reconcile(String operationId) {
        HarnessOperation operation = required(operationId);
        var governance = toolsetResolver.resolve(selectionContext(operation))
                .resolveIdentity(operation.toolIdentity())
                .orElseThrow(() -> CheckedException.badRequest("Operation 对应的 Tool 治理版本已下线，无法自动对账"));
        coordinator.reconcile(invocation(operation, operationId), governance, operationId);
        return HarnessOperationResp.from(required(operationId));
    }

    private HarnessOperation required(String operationId) {
        String tenantId = requiredAuthenticationId(authentication.tenantId(), "租户");
        return store.find(tenantId, operationId)
                .orElseThrow(() -> CheckedException.notFound("Harness Operation 不存在"));
    }

    private HarnessInvocation invocation(HarnessOperation operation, String operationId) {
        Set<String> permissions = new LinkedHashSet<>();
        if (authentication.funcPermissionList() != null) {
            permissions.addAll(authentication.funcPermissionList());
        }
        return new HarnessInvocation(operation.tenantId(),
                requiredAuthenticationId(authentication.userId(), "用户"), operation.conversationId(), operationId,
                "control:" + UUID.randomUUID(), permissions);
    }

    private ToolsetSelectionContext selectionContext(HarnessOperation operation) {
        String userId = requiredAuthenticationId(authentication.userId(), "用户");
        Set<String> permissions = new LinkedHashSet<>();
        if (authentication.funcPermissionList() != null) {
            permissions.addAll(authentication.funcPermissionList());
        }
        String[] profiles = environment.getActiveProfiles();
        String activeEnvironment = profiles.length == 0 ? "default" : profiles[0];
        return new ToolsetSelectionContext(operation.tenantId(), userId, operation.conversationId(),
                "control:" + UUID.randomUUID(), "BUSINESS", "OPERATION_CONTROL", activeEnvironment, permissions,
                Set.of(operation.toolsetId()), Set.of());
    }

    private static String requiredAuthenticationId(Long value, String subject) {
        if (value == null || value <= 0) {
            throw CheckedException.badRequest("认证上下文缺少" + subject + "信息");
        }
        return value.toString();
    }
}
