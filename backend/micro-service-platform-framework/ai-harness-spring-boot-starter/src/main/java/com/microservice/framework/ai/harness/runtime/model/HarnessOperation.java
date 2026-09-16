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

package com.microservice.framework.ai.harness.runtime.model;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import java.time.Instant;
import java.util.Objects;

/**
 * 单次受治理 Tool 写操作的持久化快照。
 *
 * <p>每次状态变化都产生新对象，便于 Store 使用乐观锁和 fencing token 拒绝过期写入。</p>
 *
 * @param operationId 操作标识
 * @param tenantId 租户标识
 * @param actorUserId 原始操作人标识
 * @param conversationId 会话标识
 * @param initialTurnId 首次用户轮标识
 * @param toolsetId Tool 所属 Toolset 标识
 * @param toolIdentity Tool 稳定身份
 * @param toolCallId LangChain4j Tool Call 标识
 * @param argumentsDigest 参数摘要
 * @param idempotencyKey 业务幂等键
 * @param status 当前状态
 * @param confirmationRequired 是否需要用户确认
 * @param approvalRequired 是否需要独立审批
 * @param fencingToken 租约围栏令牌
 * @param leaseOwner 当前租约持有者
 * @param leaseExpiresAt 租约失效时间
 * @param confirmedTurnId 完成确认的用户轮标识
 * @param approvedBy 独立审批人标识
 * @param resultText 脱敏后的执行结果
 * @param errorMessage 中文失败原因
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @param version 持久化乐观锁版本
 * @author xJh
 * @since 2026-07-18
 */
// Operation 是数据库乐观锁快照，保持字段扁平以避免持久化映射和原子更新语义分裂。
// CHECKSTYLE:OFF
public record HarnessOperation(String operationId, String tenantId, String actorUserId, String conversationId,
        String initialTurnId, String toolsetId, String toolIdentity, String toolCallId, String argumentsDigest,
        String idempotencyKey,
        HarnessOperationStatus status, boolean confirmationRequired, boolean approvalRequired,
        String approvalTarget, String approvalChange, String approvalRisk, Instant approvalExpiresAt,
        long fencingToken, String leaseOwner, Instant leaseExpiresAt,
        String confirmedTurnId, String approvedBy, String resultText, String errorMessage, Instant createdAt,
        Instant updatedAt, int version) {
    // CHECKSTYLE:ON

    public HarnessOperation {
        operationId = requireText(operationId, "操作标识");
        tenantId = requireText(tenantId, "租户标识");
        actorUserId = requireText(actorUserId, "操作人标识");
        conversationId = requireText(conversationId, "会话标识");
        initialTurnId = requireText(initialTurnId, "初始用户轮标识");
        toolsetId = requireText(toolsetId, "Toolset 标识");
        toolIdentity = requireText(toolIdentity, "工具身份");
        toolCallId = requireText(toolCallId, "Tool Call 标识");
        argumentsDigest = requireText(argumentsDigest, "参数摘要");
        idempotencyKey = requireText(idempotencyKey, "幂等键");
        status = Objects.requireNonNull(status, "操作状态不能为空");
        createdAt = Objects.requireNonNull(createdAt, "创建时间不能为空");
        updatedAt = Objects.requireNonNull(updatedAt, "更新时间不能为空");
        if (version < 0) {
            throw new IllegalArgumentException("Operation 版本不能为负数");
        }
    }

    /** 创建使用默认脱敏投影的 intent，保留 Framework 调用兼容。 */
    public static HarnessOperation intent(HarnessInvocation invocation, ToolGovernance governance,
            String toolCallId, String argumentsDigest, Instant now) {
        ApprovalSummary summary = new ApprovalSummary("业务对象", "执行受治理的业务变更",
                "风险等级：" + governance.risk().name());
        Instant expiresAt = governance.approvalRequired() ? now.plus(java.time.Duration.ofHours(24)) : null;
        return intent(invocation, governance, toolCallId, argumentsDigest, summary, expiresAt, now);
    }

    /** 创建带业务审批投影且必须在外部写调用前持久化的 intent。 */
    public static HarnessOperation intent(HarnessInvocation invocation, ToolGovernance governance,
            String toolCallId, String argumentsDigest, ApprovalSummary approvalSummary,
            Instant approvalExpiresAt, Instant now) {
        return new HarnessOperation(invocation.operationId(), invocation.tenantId(), invocation.userId(),
                invocation.conversationId(), invocation.turnId(), governance.toolsetId(), governance.identity(), toolCallId,
                argumentsDigest, invocation.tenantId() + ":" + invocation.operationId() + ":" + argumentsDigest,
                HarnessOperationStatus.INTENT_RECORDED, governance.confirmationRequired(),
                governance.approvalRequired(), approvalSummary.target(), approvalSummary.change(),
                approvalSummary.risk(), approvalExpiresAt, 0, null, null, null, null, null, null, now, now, 0);
    }

    public HarnessOperation withLease(String owner, Instant expiresAt, long token) {
        return copy(HarnessOperationStatus.EXECUTING, token, owner, expiresAt, confirmedTurnId, approvedBy,
                resultText, errorMessage, Instant.now());
    }

    public HarnessOperation withStatus(HarnessOperationStatus newStatus, String message) {
        return copy(newStatus, fencingToken, leaseOwner, leaseExpiresAt, confirmedTurnId, approvedBy,
                resultText, message, Instant.now());
    }

    public HarnessOperation confirmed(String turnId, HarnessOperationStatus nextStatus, Instant now) {
        return copy(nextStatus, fencingToken, leaseOwner, leaseExpiresAt, turnId, approvedBy, resultText,
                errorMessage, now);
    }

    public HarnessOperation approved(String approver, Instant now) {
        return copy(HarnessOperationStatus.INTENT_RECORDED, fencingToken, leaseOwner, leaseExpiresAt,
                confirmedTurnId, approver, resultText, null, now);
    }

    public HarnessOperation succeeded(String result, Instant now) {
        return copy(HarnessOperationStatus.SUCCEEDED, fencingToken, null, null, confirmedTurnId, approvedBy,
                result, null, now);
    }

    public HarnessOperation resultUnknown(String error, Instant now) {
        return copy(HarnessOperationStatus.RESULT_UNKNOWN, fencingToken, null, null, confirmedTurnId, approvedBy,
                resultText, error, now);
    }

    public HarnessOperation failed(String error, Instant now) {
        return copy(HarnessOperationStatus.FAILED, fencingToken, null, null, confirmedTurnId, approvedBy,
                resultText, error, now);
    }

    private HarnessOperation copy(HarnessOperationStatus newStatus, long newToken, String newLeaseOwner,
            Instant newLeaseExpiresAt, String newConfirmedTurnId, String newApprovedBy, String newResult,
            String newError, Instant now) {
        return new HarnessOperation(operationId, tenantId, actorUserId, conversationId, initialTurnId,
                toolsetId, toolIdentity, toolCallId, argumentsDigest, idempotencyKey, newStatus,
                confirmationRequired, approvalRequired, approvalTarget, approvalChange, approvalRisk,
                approvalExpiresAt, newToken, newLeaseOwner, newLeaseExpiresAt, newConfirmedTurnId,
                newApprovedBy, newResult, newError, createdAt, now, version);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
