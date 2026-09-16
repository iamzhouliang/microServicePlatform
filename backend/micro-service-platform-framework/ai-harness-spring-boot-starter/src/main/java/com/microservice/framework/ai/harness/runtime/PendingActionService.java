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

package com.microservice.framework.ai.harness.runtime;

import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import java.time.Clock;
import java.util.Objects;

/**
 * 跨用户轮确认与独立审批服务。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class PendingActionService {
    
    private final HarnessOperationStore store;
    private final Clock clock;
    
    public PendingActionService(HarnessOperationStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "Operation Store 不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }
    
    /**
     * 确认必须由原操作人在后续 HTTP 用户轮完成。
     *
     * @param actor 当前操作人身份
     * @param operationId 待确认 Operation 标识
     * @return 确认后的 Operation
     * @throws IllegalArgumentException 当身份、会话、用户轮或状态不匹配时抛出
     */
    public HarnessOperation confirm(HarnessInvocation actor, String operationId) {
        HarnessOperation operation = owned(actor, operationId, true);
        if (operation.initialTurnId().equals(actor.turnId())) {
            throw new IllegalArgumentException("不能在同一用户轮确认高风险操作");
        }
        HarnessOperationStatus next = operation.approvalRequired()
                ? HarnessOperationStatus.WAITING_APPROVAL
                : HarnessOperationStatus.INTENT_RECORDED;
        return store.save(operation.confirmed(actor.turnId(), next, clock.instant()));
    }
    
    /**
     * 审批人与原操作人必须分离，审批不替代原操作人的权限。
     *
     * @param approver 独立审批人身份
     * @param operationId 待审批 Operation 标识
     * @return 审批后的 Operation
     * @throws IllegalArgumentException 当审批人或状态不合法时抛出
     */
    public HarnessOperation approve(HarnessInvocation approver, String operationId) {
        HarnessOperation operation = find(approver.tenantId(), operationId);
        if (operation.actorUserId().equals(approver.userId())) {
            throw new IllegalArgumentException("审批人不能是原操作人");
        }
        if (operation.status() != HarnessOperationStatus.WAITING_APPROVAL) {
            throw new IllegalArgumentException("当前 Operation 不在待审批状态");
        }
        requireApprovalNotExpired(operation);
        return store.save(operation.approved(approver.userId(), clock.instant()));
    }
    
    /**
     * 独立审批人拒绝操作后直接终止，不允许模型自动恢复。
     *
     * @param approver 独立审批人身份
     * @param operationId 待拒绝 Operation 标识
     * @param reason 拒绝原因
     * @return 已终止的 Operation
     * @throws IllegalArgumentException 当审批人或状态不合法时抛出
     */
    public HarnessOperation reject(HarnessInvocation approver, String operationId, String reason) {
        HarnessOperation operation = find(approver.tenantId(), operationId);
        if (operation.actorUserId().equals(approver.userId())) {
            throw new IllegalArgumentException("审批人不能是原操作人");
        }
        if (operation.status() != HarnessOperationStatus.WAITING_APPROVAL) {
            throw new IllegalArgumentException("当前 Operation 不在待审批状态");
        }
        requireApprovalNotExpired(operation);
        String message = reason == null || reason.isBlank() ? "审批人已拒绝该操作" : "审批已拒绝：" + reason.trim();
        return store.save(operation.failed(message, clock.instant()));
    }
    
    /**
     * 每次恢复执行前重新校验绑定关系、原 actor 当前权限、确认和审批状态。
     *
     * @param actor 原操作人当前身份
     * @param governance Tool 当前治理规则
     * @return 是否允许继续执行的门控结果
     */
    public Gate evaluate(HarnessInvocation actor, ToolGovernance governance) {
        HarnessOperation operation = owned(actor, actor.operationId(), true);
        if (!hasRequiredPermissions(actor, governance.requiredPermission())) {
            return Gate.blocked("原操作人的权限已发生变化，已拒绝恢复执行");
        }
        if (governance.confirmationRequired() && operation.confirmedTurnId() == null) {
            return Gate.waiting(HarnessOperationStatus.WAITING_CONFIRMATION,
                    "该操作需要您在下一条消息中明确确认");
        }
        if (governance.approvalRequired() && operation.approvedBy() == null) {
            requireApprovalNotExpired(operation);
            return Gate.waiting(HarnessOperationStatus.WAITING_APPROVAL, "该操作正在等待独立审批人审批");
        }
        return Gate.allowed();
    }
    
    public HarnessOperation markWaiting(HarnessInvocation actor, Gate gate) {
        HarnessOperation operation = owned(actor, actor.operationId(), true);
        return store.save(operation.withStatus(gate.status(), gate.message()));
    }
    
    private HarnessOperation owned(HarnessInvocation actor, String operationId, boolean requireConversation) {
        HarnessOperation operation = find(actor.tenantId(), operationId);
        if (!operation.actorUserId().equals(actor.userId())) {
            throw new IllegalArgumentException("待处理操作不属于当前用户");
        }
        if (requireConversation && !operation.conversationId().equals(actor.conversationId())) {
            throw new IllegalArgumentException("待处理操作不属于当前会话");
        }
        return operation;
    }
    
    private HarnessOperation find(String tenantId, String operationId) {
        return store.find(tenantId, operationId)
                .orElseThrow(() -> new IllegalArgumentException("待处理操作不存在或已过期"));
    }
    
    private void requireApprovalNotExpired(HarnessOperation operation) {
        if (operation.approvalExpiresAt() != null && !clock.instant().isBefore(operation.approvalExpiresAt())) {
            throw new IllegalArgumentException("审批已过期，请由原操作人重新发起业务请求");
        }
    }
    
    private static boolean hasRequiredPermissions(HarnessInvocation actor, String expression) {
        if ("*".equals(expression)) {
            return true;
        }
        return java.util.Arrays.stream(expression.split(","))
                .map(String::trim)
                .filter(permission -> !permission.isEmpty())
                .allMatch(actor.permissions()::contains);
    }
    
    /**
     * Tool 执行门控结果。
     *
     * @param ready 是否允许执行
     * @param status 不允许执行时应持久化的状态
     * @param message 面向模型的中文约束说明
     */
    public record Gate(boolean ready, HarnessOperationStatus status, String message) {
        public static Gate allowed() {
            return new Gate(true, HarnessOperationStatus.INTENT_RECORDED, null);
        }

        public static Gate waiting(HarnessOperationStatus status, String message) {
            return new Gate(false, status, message);
        }

        public static Gate blocked(String message) {
            return new Gate(false, HarnessOperationStatus.FAILED, message);
        }
    }
}
