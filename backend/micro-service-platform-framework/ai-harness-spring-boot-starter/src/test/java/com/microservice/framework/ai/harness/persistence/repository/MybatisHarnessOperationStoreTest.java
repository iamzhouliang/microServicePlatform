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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microservice.framework.ai.harness.persistence.entity.HarnessOperationEntity;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * MyBatis Operation Store 待恢复状态查询测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class MybatisHarnessOperationStoreTest {
    
    @Test
    void pendingLookupShouldIncludeApprovedIntentReadyForResume() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                HarnessOperationEntity.class);
        HarnessOperationMapper mapper = mock(HarnessOperationMapper.class);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(null);
        MybatisHarnessOperationStore store = new MybatisHarnessOperationStore(mapper);
        
        store.findPending("1", "2", "3");
        
        verify(mapper).recoverExpiredExecution(eq(1L), eq(2L), eq(3L), any(Instant.class), anyString(), eq(false));
        ArgumentCaptor<Wrapper<HarnessOperationEntity>> wrapper = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectOne(wrapper.capture());
        wrapper.getValue().getSqlSegment();
        Map<String, Object> parameters = ((AbstractWrapper<?, ?, ?>) wrapper.getValue()).getParamNameValuePairs();
        assertThat(parameters.values()).contains(
                HarnessOperationStatus.INTENT_RECORDED.name(),
                HarnessOperationStatus.WAITING_CONFIRMATION.name(),
                HarnessOperationStatus.WAITING_APPROVAL.name(),
                HarnessOperationStatus.RESULT_UNKNOWN.name());
    }
    
    @Test
    void saveShouldUseOperationSnapshotVersionInsteadOfLatestDatabaseVersion() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                HarnessOperationEntity.class);
        HarnessOperationMapper mapper = mock(HarnessOperationMapper.class);
        HarnessOperationEntity latest = entity(8);
        HarnessOperationEntity afterSave = entity(4);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(latest, afterSave);
        when(mapper.updateById(any(HarnessOperationEntity.class))).thenReturn(1);
        MybatisHarnessOperationStore store = new MybatisHarnessOperationStore(mapper);
        HarnessOperation staleSnapshot = operation(3);
        
        store.save(staleSnapshot);
        
        ArgumentCaptor<HarnessOperationEntity> updated = ArgumentCaptor.forClass(HarnessOperationEntity.class);
        verify(mapper).updateById(updated.capture());
        assertThat(updated.getValue().getVersion()).isEqualTo(3);
    }
    
    private static HarnessOperation operation(int version) {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        return new HarnessOperation("operation-1", "1", "2", "3", "turn-1",
                "module:iam:user-management", "iam:provision_user@1.0.0",
                "call-1", "digest", "1:operation-1:digest", HarnessOperationStatus.SUCCEEDED,
                true, true, "用户账号：zhangsan", "创建用户并分配角色", "高风险",
                now.plusSeconds(3600), 1, null, null, "turn-2", "4", "创建成功", null, now, now, version);
    }
    
    private static HarnessOperationEntity entity(int version) {
        Instant now = Instant.parse("2026-07-20T00:00:00Z");
        HarnessOperationEntity entity = new HarnessOperationEntity();
        entity.setId(10L);
        entity.setOperationId("operation-1");
        entity.setTenantId(1L);
        entity.setActorUserId(2L);
        entity.setConversationId(3L);
        entity.setInitialTurnId("turn-1");
        entity.setToolsetId("module:iam:user-management");
        entity.setToolIdentity("iam:provision_user@1.0.0");
        entity.setToolCallId("call-1");
        entity.setArgumentsDigest("digest");
        entity.setIdempotencyKey("1:operation-1:digest");
        entity.setStatus(HarnessOperationStatus.SUCCEEDED.name());
        entity.setConfirmationRequired(true);
        entity.setApprovalRequired(true);
        entity.setApprovalTarget("用户账号：zhangsan");
        entity.setApprovalChange("创建用户并分配角色");
        entity.setApprovalRisk("高风险");
        entity.setApprovalExpiresAt(now.plusSeconds(3600));
        entity.setFencingToken(1L);
        entity.setConfirmedTurnId("turn-2");
        entity.setApprovedBy(4L);
        entity.setResultText("创建成功");
        entity.setVersion(version);
        entity.setDeleted(false);
        entity.setCreateTime(now);
        entity.setLastModifyTime(now);
        return entity;
    }
}
