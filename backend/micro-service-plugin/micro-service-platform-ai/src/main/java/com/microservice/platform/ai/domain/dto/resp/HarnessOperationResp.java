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

import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import java.time.Instant;

/**
 * 后台审计使用的 Operation 投影，不包含参数正文、令牌或模型私有推理。
 *
 * @param operationId Operation 标识
 * @param actorUserId 原操作人标识
 * @param conversationId 会话标识
 * @param toolsetId Toolset 标识
 * @param toolIdentity Tool 稳定身份
 * @param status 当前状态
 * @param fencingToken 租约围栏令牌
 * @param confirmedTurnId 完成确认的用户轮标识
 * @param approvedBy 独立审批人标识
 * @param errorMessage 脱敏后的中文失败原因
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 * @author xJh
 * @since 2026-07-18
 */
// 后台审计响应与 Operation 快照字段一一对应，保持扁平以避免暴露内部嵌套结构。
// CHECKSTYLE:OFF
public record HarnessOperationResp(String operationId, String actorUserId, String conversationId,
        String toolsetId, String toolIdentity, String status, String approvalTarget, String approvalChange, String approvalRisk,
        Instant approvalExpiresAt, long fencingToken, String confirmedTurnId, String approvedBy,
        String errorMessage, Instant createdAt, Instant updatedAt) {
    // CHECKSTYLE:ON

    public static HarnessOperationResp from(HarnessOperation operation) {
        return new HarnessOperationResp(operation.operationId(), operation.actorUserId(),
                operation.conversationId(), operation.toolsetId(), operation.toolIdentity(), operation.status().name(),
                operation.approvalTarget(), operation.approvalChange(), operation.approvalRisk(),
                operation.approvalExpiresAt(),
                operation.fencingToken(), operation.confirmedTurnId(), operation.approvedBy(),
                operation.errorMessage(), operation.createdAt(), operation.updatedAt());
    }
}
