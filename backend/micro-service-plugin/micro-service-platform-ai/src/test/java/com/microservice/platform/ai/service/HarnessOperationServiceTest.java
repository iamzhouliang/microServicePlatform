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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperationStatus;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.platform.ai.domain.dto.req.HarnessOperationDecisionReq;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;

/**
 * Harness Operation 控制面可信认证上下文测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class HarnessOperationServiceTest {

    @Test
    void shouldRejectMissingTenantBeforeReadingOperation() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        HarnessOperationStore store = mock(HarnessOperationStore.class);
        HarnessOperationService service = service(authentication, store, mock(PendingActionService.class));

        assertThatThrownBy(() -> service.get("operation-1"))
                .hasMessageContaining("认证上下文缺少租户信息");
        verifyNoInteractions(store);
    }

    @Test
    void shouldRejectMissingApproverBeforeChangingOperation() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.tenantId()).thenReturn(1L);
        HarnessOperationStore store = mock(HarnessOperationStore.class);
        when(store.find("1", "operation-1")).thenReturn(Optional.of(waitingApproval()));
        PendingActionService pendingActionService = mock(PendingActionService.class);
        HarnessOperationService service = service(authentication, store, pendingActionService);
        HarnessOperationDecisionReq request = new HarnessOperationDecisionReq();
        request.setApproved(true);

        assertThatThrownBy(() -> service.decide("operation-1", request))
                .hasMessageContaining("认证上下文缺少用户信息");
        verifyNoInteractions(pendingActionService);
    }

    @Test
    void shouldExposeOnlyRedactedApprovalProjection() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.tenantId()).thenReturn(1L);
        HarnessOperationStore store = mock(HarnessOperationStore.class);
        when(store.find("1", "operation-1")).thenReturn(Optional.of(waitingApproval()));

        var response = service(authentication, store, mock(PendingActionService.class)).get("operation-1");

        assertThat(response.approvalTarget()).isEqualTo("用户账号：zhangsan");
        assertThat(response.approvalChange()).contains("组织 20", "角色 [30,40]");
        assertThat(response.approvalRisk()).contains("高风险");
        assertThat(response.toString()).doesNotContain("password", "token", "13800138000");
    }

    private static HarnessOperationService service(AuthenticationContext authentication,
                                                   HarnessOperationStore store, PendingActionService pendingActionService) {
        return new HarnessOperationService(authentication, store, pendingActionService,
                mock(HarnessOperationCoordinator.class), mock(ToolsetResolver.class), mock(Environment.class));
    }

    private static HarnessOperation waitingApproval() {
        Instant now = Instant.parse("2026-07-18T04:00:00Z");
        return new HarnessOperation("operation-1", "1", "10", "conversation-1", "turn-1",
                "module:iam:user-management", "iam.provision-user@iam:1.0", "call-1", "digest", "idempotency-key",
                HarnessOperationStatus.WAITING_APPROVAL, true, true,
                "用户账号：zhangsan", "在组织 20 创建用户并分配角色 [30,40]", "高风险：创建账号",
                now.plusSeconds(3600), 0, null, null,
                "turn-2", null, null, null, now, now, 0);
    }
}
