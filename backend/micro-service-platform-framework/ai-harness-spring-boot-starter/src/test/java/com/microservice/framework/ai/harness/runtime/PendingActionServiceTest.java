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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinatorTest.RecordingStore;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PendingActionServiceTest {
    
    private RecordingStore store;
    private PendingActionService service;
    private ToolGovernance governance;
    
    @BeforeEach
    void setUp() {
        store = new RecordingStore();
        service = new PendingActionService(store,
                Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC));
        governance = new ToolGovernance("provision_user", "module:iam:user-management", "iam", "1.0.0",
                "iam:user:create",
                HarnessRiskLevel.HIGH, true, true, true, true, "verify_user", null);
        store.recordIntent(HarnessOperation.intent(actor("user-1", "turn-1"), governance,
                "call-1", "digest-1", Instant.parse("2026-07-18T02:00:00Z")));
        store.save(store.operation().withStatus(HarnessOperationStatus.WAITING_CONFIRMATION, null));
        store.clearEvents();
    }
    
    @Test
    void shouldRequireConfirmationFromANewerHttpTurn() {
        assertThatThrownBy(() -> service.confirm(actor("user-1", "turn-1"), "operation-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能在同一用户轮");
        
        HarnessOperation confirmed = service.confirm(actor("user-1", "turn-2"), "operation-1");
        
        assertThat(confirmed.confirmedTurnId()).isEqualTo("turn-2");
        assertThat(confirmed.status()).isEqualTo(HarnessOperationStatus.WAITING_APPROVAL);
    }
    
    @Test
    void shouldSeparateApproverAndRecheckOriginalActorPermission() {
        service.confirm(actor("user-1", "turn-2"), "operation-1");
        
        assertThatThrownBy(() -> service.approve(actor("user-1", "turn-3"), "operation-1"))
                .hasMessageContaining("审批人不能是原操作人");
        store.save(store.operation().withStatus(HarnessOperationStatus.WAITING_APPROVAL,
                "该操作正在等待独立审批人审批"));
        HarnessOperation approved = service.approve(actor("approver-1", "turn-3"), "operation-1");
        
        assertThat(approved.errorMessage()).isNull();
        
        HarnessInvocation originalWithoutPermission = new HarnessInvocation("tenant-1", "user-1",
                "conversation-1", "operation-1", "turn-4", Set.of());
        assertThat(service.evaluate(originalWithoutPermission, governance).ready()).isFalse();
        assertThat(service.evaluate(originalWithoutPermission, governance).message()).contains("权限已发生变化");
        assertThat(service.evaluate(actor("user-1", "turn-4"), governance).ready()).isTrue();
    }
    
    @Test
    void shouldReturnToExecutableStateWhenConfirmationDoesNotNeedApproval() {
        RecordingStore localStore = new RecordingStore();
        PendingActionService localService = new PendingActionService(localStore,
                Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC));
        ToolGovernance confirmationOnly = new ToolGovernance("update_profile", "module:iam:user-management",
                "iam", "1.0.0",
                "iam:user:create", HarnessRiskLevel.MEDIUM, true, true, true, false, null, null);
        localStore.recordIntent(HarnessOperation.intent(actor("user-1", "turn-1"), confirmationOnly,
                "call-2", "digest-2", Instant.parse("2026-07-18T02:00:00Z")));
        localStore.save(localStore.operation().withStatus(HarnessOperationStatus.WAITING_CONFIRMATION, null));
        
        HarnessOperation confirmed = localService.confirm(actor("user-1", "turn-2"), "operation-1");
        
        assertThat(confirmed.status()).isEqualTo(HarnessOperationStatus.INTENT_RECORDED);
    }
    
    @Test
    void shouldAcceptCompositePermissionExpressionWhenActorOwnsEveryPermission() {
        RecordingStore localStore = new RecordingStore();
        PendingActionService localService = new PendingActionService(localStore,
                Clock.fixed(Instant.parse("2026-07-18T02:00:00Z"), ZoneOffset.UTC));
        ToolGovernance composite = new ToolGovernance("provision_user", "module:iam:user-management",
                "iam", "1.0.0",
                "iam:user:create,iam:role:assign", HarnessRiskLevel.HIGH,
                true, true, false, false, null, null);
        HarnessInvocation actor = new HarnessInvocation("tenant-1", "user-1", "conversation-1",
                "operation-1", "turn-1", Set.of("iam:user:create", "iam:role:assign"));
        localStore.recordIntent(HarnessOperation.intent(actor, composite,
                "call-1", "digest-1", Instant.parse("2026-07-18T02:00:00Z")));
        
        assertThat(localService.evaluate(actor, composite).ready()).isTrue();
    }
    
    private static HarnessInvocation actor(String userId, String turnId) {
        return new HarnessInvocation("tenant-1", userId, "conversation-1", "operation-1", turnId,
                Set.of("iam:user:create"));
    }
}
