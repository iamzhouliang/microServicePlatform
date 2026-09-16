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

package com.microservice.framework.ai.harness.persistence.repository;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.microservice.framework.ai.harness.persistence.entity.HarnessOperationEntity;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 基于 MyBatis-Plus 的 Operation/Saga Store。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Repository
@RequiredArgsConstructor
public class MybatisHarnessOperationStore implements HarnessOperationStore {
    
    private final HarnessOperationMapper mapper;
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HarnessOperation recordIntent(HarnessOperation intent) {
        Optional<HarnessOperation> existing = find(intent.tenantId(), intent.operationId());
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        HarnessOperationEntity entity = toEntity(intent);
        entity.setId(IdWorker.getId());
        entity.setVersion(0);
        entity.setDeleted(false);
        entity.setCreateTime(intent.createdAt());
        entity.setLastModifyTime(intent.updatedAt());
        try {
            mapper.insert(entity);
        } catch (RuntimeException concurrentInsert) {
            return find(intent.tenantId(), intent.operationId()).orElseThrow(() -> concurrentInsert);
        }
        return toModel(entity);
    }
    
    @Override
    public Optional<HarnessOperation> find(String tenantId, String operationId) {
        Long tenant = parseId(tenantId, "租户标识");
        return Optional.ofNullable(mapper.selectOne(Wraps.<HarnessOperationEntity>lbQ()
                .eq(HarnessOperationEntity::getTenantId, tenant)
                .eq(HarnessOperationEntity::getOperationId, operationId)))
                .map(MybatisHarnessOperationStore::toModel);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Optional<HarnessOperation> findPending(String tenantId, String actorUserId, String conversationId) {
        Long tenant = parseId(tenantId, "租户标识");
        Long actor = parseId(actorUserId, "用户标识");
        Long conversation = parseId(conversationId, "会话标识");
        mapper.recoverExpiredExecution(tenant, actor, conversation, Instant.now(),
                "执行租约已过期，结果未知，必须通过权威校验器对账", false);
        // 独立审批完成后状态回到 INTENT_RECORDED，后续用户轮必须继续复用原 Operation。
        List<String> statuses = List.of(HarnessOperationStatus.INTENT_RECORDED.name(),
                HarnessOperationStatus.WAITING_CONFIRMATION.name(), HarnessOperationStatus.WAITING_APPROVAL.name(),
                HarnessOperationStatus.RESULT_UNKNOWN.name());
        return Optional.ofNullable(mapper.selectOne(Wraps.<HarnessOperationEntity>lbQ()
                .eq(HarnessOperationEntity::getTenantId, tenant)
                .eq(HarnessOperationEntity::getActorUserId, actor)
                .eq(HarnessOperationEntity::getConversationId, conversation)
                .in(HarnessOperationEntity::getStatus, statuses)
                .orderByDesc(HarnessOperationEntity::getLastModifyTime).last("LIMIT 1")))
                .map(MybatisHarnessOperationStore::toModel);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HarnessOperation claimLease(HarnessOperation operation, String leaseOwner, Instant expiresAt) {
        HarnessOperationEntity stored = requireEntity(operation);
        Instant now = Instant.now();
        if (mapper.claimLease(stored.getId(), stored.getTenantId(), operation.version(), leaseOwner,
                expiresAt, now, false) != 1) {
            throw new IllegalStateException("Operation 执行租约申请失败，可能已被其他实例接管");
        }
        return toModel(requireEntity(operation));
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public HarnessOperation save(HarnessOperation operation) {
        HarnessOperationEntity stored = requireEntity(operation);
        copyMutable(operation, stored);
        // 必须使用调用方持有的快照版本，让 MyBatis 乐观锁拒绝迟到的旧执行者。
        stored.setVersion(operation.version());
        if (mapper.updateById(stored) != 1) {
            throw new IllegalStateException("Operation 并发更新被拒绝：" + operation.operationId());
        }
        return toModel(requireEntity(operation));
    }
    
    private HarnessOperationEntity requireEntity(HarnessOperation operation) {
        return mapper.selectOne(Wraps.<HarnessOperationEntity>lbQ()
                .eq(HarnessOperationEntity::getTenantId, parseId(operation.tenantId(), "租户标识"))
                .eq(HarnessOperationEntity::getOperationId, operation.operationId()));
    }
    
    private static HarnessOperationEntity toEntity(HarnessOperation value) {
        HarnessOperationEntity entity = new HarnessOperationEntity();
        entity.setOperationId(value.operationId());
        entity.setTenantId(parseId(value.tenantId(), "租户标识"));
        entity.setActorUserId(parseId(value.actorUserId(), "用户标识"));
        entity.setConversationId(parseId(value.conversationId(), "会话标识"));
        entity.setInitialTurnId(value.initialTurnId());
        entity.setToolsetId(value.toolsetId());
        entity.setToolIdentity(value.toolIdentity());
        entity.setToolCallId(value.toolCallId());
        entity.setArgumentsDigest(value.argumentsDigest());
        entity.setIdempotencyKey(value.idempotencyKey());
        entity.setConfirmationRequired(value.confirmationRequired());
        entity.setApprovalRequired(value.approvalRequired());
        entity.setApprovalTarget(value.approvalTarget());
        entity.setApprovalChange(value.approvalChange());
        entity.setApprovalRisk(value.approvalRisk());
        entity.setApprovalExpiresAt(value.approvalExpiresAt());
        copyMutable(value, entity);
        return entity;
    }
    
    private static void copyMutable(HarnessOperation value, HarnessOperationEntity entity) {
        entity.setStatus(value.status().name());
        entity.setFencingToken(value.fencingToken());
        entity.setLeaseOwner(value.leaseOwner());
        entity.setLeaseExpiresAt(value.leaseExpiresAt());
        entity.setConfirmedTurnId(value.confirmedTurnId());
        entity.setApprovedBy(value.approvedBy() == null ? null : parseId(value.approvedBy(), "审批人标识"));
        entity.setResultText(value.resultText());
        entity.setErrorMessage(value.errorMessage());
        entity.setLastModifyTime(value.updatedAt());
    }
    
    private static HarnessOperation toModel(HarnessOperationEntity entity) {
        return new HarnessOperation(entity.getOperationId(), entity.getTenantId().toString(),
                entity.getActorUserId().toString(), entity.getConversationId().toString(), entity.getInitialTurnId(),
                entity.getToolsetId(), entity.getToolIdentity(), entity.getToolCallId(), entity.getArgumentsDigest(),
                entity.getIdempotencyKey(), HarnessOperationStatus.valueOf(entity.getStatus()),
                Boolean.TRUE.equals(entity.getConfirmationRequired()), Boolean.TRUE.equals(entity.getApprovalRequired()),
                entity.getApprovalTarget(), entity.getApprovalChange(), entity.getApprovalRisk(),
                entity.getApprovalExpiresAt(),
                entity.getFencingToken() == null ? 0 : entity.getFencingToken(), entity.getLeaseOwner(),
                entity.getLeaseExpiresAt(), entity.getConfirmedTurnId(),
                entity.getApprovedBy() == null ? null : entity.getApprovedBy().toString(), entity.getResultText(),
                entity.getErrorMessage(), entity.getCreateTime(), entity.getLastModifyTime(),
                entity.getVersion() == null ? 0 : entity.getVersion());
    }
    
    private static Long parseId(String value, String field) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(field + "必须是正整数", failure);
        }
    }
}
