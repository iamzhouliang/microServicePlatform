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

package com.microservice.platform.ai.core.agent.tool.iam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.UncertainToolExecutionException;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolService;
import feign.FeignException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * IAM 原子 Tool 暴露、授权、幂等回读与结果未知测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class IamAgentToolServiceTest {

    @Test
    void shouldExposeOnlyReadToolsAndAtomicProvisionCommand() {
        List<AiServiceTool> tools = ToolService.findTools(new IamAgentToolService(mock(IamAgentFeign.class),
                () -> "token"));

        assertThat(tools).extracting(AiServiceTool::name).containsExactlyInAnyOrder(
                IamAgentTools.RESOLVE_ORG, IamAgentTools.RESOLVE_ROLE, IamAgentTools.SEARCH_USER,
                IamAgentTools.VERIFY_USER, IamAgentTools.PROVISION_USER);
        assertThat(tools).extracting(AiServiceTool::name)
                .allMatch(name -> name.matches("^[a-zA-Z0-9_-]+$"));
        assertThat(tools).extracting(AiServiceTool::name)
                .doesNotContain("iam.create-user", "iam.assign-roles", "iam.delete-user");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldExposeOrganizationAndRoleNamesWithoutInternalRelationIds() {
        IamAgentFeign feign = mock(IamAgentFeign.class);
        AgentUserResp response = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .convertValue(Map.of(
                        "id", 1L,
                        "username", "admin",
                        "nickname", "平台管理员",
                        "orgId", 100L,
                        "orgName", "平台总部",
                        "status", true,
                        "roleIds", List.of(1L, 2L),
                        "roleNames", List.of("平台管理员", "审计员")), AgentUserResp.class);
        when(feign.searchUsers(eq("Bearer invocation-token"), any())).thenReturn(List.of(response));
        IamAgentToolService service = new IamAgentToolService(feign, () -> "wrong-token");

        Map<String, Object> result = service.searchUser(parameters(), "admin", null, null, null);
        Map<String, Object> user = (Map<String, Object>) ((List<?>) result.get("users")).getFirst();

        assertThat(user).containsEntry("orgName", "平台总部")
                .containsEntry("roleNames", List.of("平台管理员", "审计员"))
                .doesNotContainKeys("orgId", "roleIds");
    }

    @Test
    void shouldInjectOperationIdThroughOfficialInvocationParameters() {
        IamAgentFeign feign = mock(IamAgentFeign.class);
        AgentUserResp response = AgentUserResp.builder().id(101L).username("zhangsan").nickname("张三")
                .orgId(11L).status(true).roleIds(List.of(21L)).build();
        when(feign.provisionUser(eq("Bearer invocation-token"), any(AgentUserProvisionReq.class)))
                .thenReturn(response);
        IamAgentToolService service = new IamAgentToolService(feign, () -> "wrong-token");
        AiServiceTool tool = ToolService.findTools(service).stream()
                .filter(item -> IamAgentTools.PROVISION_USER.equals(item.name())).findFirst().orElseThrow();
        HarnessInvocation invocation = new HarnessInvocation("1", "10", "100", "operation-1", "turn-1",
                Set.of("sys:user:add", "sys:role:assign-users"));
        InvocationParameters parameters = InvocationParameters.from(java.util.Map.of(
                HarnessInvocation.PARAMETER_KEY, invocation,
                DelegatedAuthorization.PARAMETER_KEY, new DelegatedAuthorization("invocation-token")));
        InvocationContext context = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId(100L).invocationParameters(parameters).build();
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("call-1")
                .name(IamAgentTools.PROVISION_USER)
                .arguments("{\"username\":\"zhangsan\",\"nickname\":\"张三\","
                        + "\"mobile\":\"13800138000\",\"orgId\":\"11\",\"roleIds\":[\"21\"]}")
                .build();

        assertThat(tool.toolExecutor().executeWithContext(request, context).resultText()).contains("zhangsan");
        verify(feign).provisionUser(eq("Bearer invocation-token"),
                org.mockito.ArgumentMatchers.argThat(req -> "operation-1".equals(req.getOperationKey())
                        && req.getRoleIds().equals(List.of(21L))));
    }

    @Test
    void capabilityShouldDeclareHighRiskAtomicGovernance() {
        IamAgentToolsetContributor provider = new IamAgentToolsetContributor(
                new IamAgentToolService(mock(IamAgentFeign.class), () -> "token"));

        ToolGovernance governance = provider.contribute(null).iterator().next().governanceByName().values().stream()
                .filter(rule -> IamAgentTools.PROVISION_USER.equals(rule.toolName())).findFirst().orElseThrow();

        assertThat(governance.write()).isTrue();
        assertThat(governance.confirmationRequired()).isTrue();
        assertThat(governance.approvalRequired()).isTrue();
        assertThat(governance.requiredPermission()).contains("sys:user:add", "sys:role:assign-users");
    }

    @Test
    void shouldReadAuthoritativeReceiptAfterFeignServerFailure() {
        IamAgentFeign feign = mock(IamAgentFeign.class);
        FeignException failure = mock(FeignException.class);
        when(failure.status()).thenReturn(503);
        when(feign.provisionUser(eq("Bearer invocation-token"), any(AgentUserProvisionReq.class)))
                .thenThrow(failure);
        AgentUserResp response = AgentUserResp.builder().id(101L).username("zhangsan").nickname("张三")
                .orgId(11L).status(true).roleIds(List.of(21L)).build();
        when(feign.getProvisionResult("Bearer invocation-token", "operation-1")).thenReturn(response);
        IamAgentToolService service = new IamAgentToolService(feign, () -> "wrong-token");

        Map<String, Object> result = service.provisionUser(parameters(), "zhangsan", "张三",
                "13800138000", null, "11", List.of("21"));

        assertThat(result).containsEntry("id", "101").containsEntry("username", "zhangsan");
        verify(feign).getProvisionResult("Bearer invocation-token", "operation-1");
    }

    @Test
    void shouldMarkResultUncertainWhenAuthoritativeReceiptIsUnavailable() {
        IamAgentFeign feign = mock(IamAgentFeign.class);
        FeignException initialFailure = mock(FeignException.class);
        when(initialFailure.status()).thenReturn(503);
        FeignException receiptFailure = mock(FeignException.class);
        when(feign.provisionUser(eq("Bearer invocation-token"), any(AgentUserProvisionReq.class)))
                .thenThrow(initialFailure);
        when(feign.getProvisionResult("Bearer invocation-token", "operation-1")).thenThrow(receiptFailure);
        IamAgentToolService service = new IamAgentToolService(feign, () -> "wrong-token");

        assertThatThrownBy(() -> service.provisionUser(parameters(), "zhangsan", "张三",
                "13800138000", null, "11", List.of("21")))
                        .isInstanceOf(UncertainToolExecutionException.class)
                        .hasMessageContaining("禁止自动重试");
    }

    private static InvocationParameters parameters() {
        HarnessInvocation invocation = new HarnessInvocation("1", "10", "100", "operation-1", "turn-1",
                Set.of("sys:user:add", "sys:role:assign-users"));
        return InvocationParameters.from(java.util.Map.of(
                HarnessInvocation.PARAMETER_KEY, invocation,
                DelegatedAuthorization.PARAMETER_KEY, new DelegatedAuthorization("invocation-token")));
    }
}
