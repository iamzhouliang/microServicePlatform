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

package com.microservice.platform.iam.system.service.impl;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.wrap.query.LbqWrapper;
import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserSearchReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentOrgResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentRoleResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;
import com.microservice.platform.iam.system.domain.dto.req.UserSaveReq;
import com.microservice.platform.iam.system.domain.entity.IamAgentOperationReceipt;
import com.microservice.platform.iam.system.domain.entity.Org;
import com.microservice.platform.iam.system.domain.entity.Role;
import com.microservice.platform.iam.system.domain.entity.User;
import com.microservice.platform.iam.system.domain.entity.UserRole;
import com.microservice.platform.iam.system.repository.IamAgentOperationReceiptMapper;
import com.microservice.platform.iam.system.service.OrgService;
import com.microservice.platform.iam.system.service.RoleService;
import com.microservice.platform.iam.system.service.UserRoleService;
import com.microservice.platform.iam.system.service.UserService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author OmX
 */
@ExtendWith(MockitoExtension.class)
class IamAgentServiceImplTest {

    private static final Long TENANT_ID = 42L;

    @Mock
    private AuthenticationContext context;

    @Mock
    private UserService userService;

    @Mock
    private OrgService orgService;

    @Mock
    private RoleService roleService;

    @Mock
    private UserRoleService userRoleService;

    @Mock
    private IamAgentOperationReceiptMapper operationReceiptMapper;

    private IamAgentServiceImpl service;

    @BeforeAll
    static void initializeTableMetadata() {
        initTableInfo(User.class);
        initTableInfo(Org.class);
        initTableInfo(Role.class);
        initTableInfo(UserRole.class);
        initTableInfo(IamAgentOperationReceipt.class);
    }

    @BeforeEach
    void setUp() {
        when(context.tenantId()).thenReturn(TENANT_ID);
        lenient().when(userService.list(any(Wrapper.class))).thenReturn(List.of(
                User.builder().id(1L).tenantId(TENANT_ID).username("tenant_admin").build()));
        lenient().when(operationReceiptMapper.insert(any(IamAgentOperationReceipt.class))).thenReturn(1);
        lenient().when(operationReceiptMapper.updateById(any(IamAgentOperationReceipt.class))).thenReturn(1);
        service = new IamAgentServiceImpl(context, userService, orgService, roleService, userRoleService,
                operationReceiptMapper);
    }

    @Test
    void resolvesOrganizationByExactNameWithinCurrentTenant() {
        Org org = Org.builder().id(11L).tenantId(TENANT_ID).label("研发中心").alias("研发").build();
        when(orgService.list(any(Wrapper.class))).thenReturn(List.of(org));

        AgentOrgResp result = service.resolveOrg("研发中心");

        assertEquals(11L, result.getId());
        assertEquals("研发中心", result.getName());
        assertEquals("研发", result.getAlias());
        ArgumentCaptor<LbqWrapper<Org>> queryCaptor = wrapperCaptor();
        verify(orgService).list(queryCaptor.capture());
        assertExactTenantQuery(queryCaptor.getValue(), "label", "研发中心");
    }

    @Test
    void resolvesRoleByExactNameWithinCurrentTenant() {
        Role role = Role.builder().id(21L).tenantId(TENANT_ID).name("仓库管理员").code("WMS-ADMIN")
                .superRole(true).build();
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(role));

        AgentRoleResp result = service.resolveRole("仓库管理员");

        assertEquals(21L, result.getId());
        assertEquals("仓库管理员", result.getName());
        assertEquals("WMS-ADMIN", result.getCode());
        assertTrue(result.getSuperRole());
        ArgumentCaptor<LbqWrapper<Role>> queryCaptor = wrapperCaptor();
        verify(roleService).list(queryCaptor.capture());
        assertExactTenantQuery(queryCaptor.getValue(), "name", "仓库管理员");
    }

    @Test
    void searchesUsersWithHumanReadableOrganizationAndRoleNames() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("admin")
                .nickname("平台管理员").orgId(11L).status(true).build();
        when(userService.list(any(Wrapper.class))).thenReturn(List.of(user));
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(101L).roleId(21L).build(),
                UserRole.builder().userId(101L).roleId(22L).build()));
        when(orgService.list(any(Wrapper.class))).thenReturn(List.of(
                Org.builder().id(11L).tenantId(TENANT_ID).label("平台总部").build()));
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(21L).tenantId(TENANT_ID).name("平台管理员").build(),
                Role.builder().id(22L).tenantId(TENANT_ID).name("审计员").build()));

        AgentUserResp result = service.searchUsers(AgentUserSearchReq.builder()
                .username("admin").exact(true).build()).getFirst();
        com.fasterxml.jackson.databind.JsonNode json =
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(result);

        assertEquals("平台总部", json.path("orgName").asText());
        assertEquals(List.of("平台管理员", "审计员"),
                new com.fasterxml.jackson.databind.ObjectMapper().convertValue(
                        json.path("roleNames"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<String>>() {
                        }));
    }

    @Test
    void searchesUserWithoutOrganizationWithoutFailing() {
        User user = User.builder().id(102L).tenantId(TENANT_ID).username("visitor")
                .nickname("访客").status(true).build();
        when(userService.list(any(Wrapper.class))).thenReturn(List.of(user));
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of());

        AgentUserResp result = service.searchUsers(AgentUserSearchReq.builder()
                .username("visitor").exact(true).build()).getFirst();

        assertEquals(null, result.getOrgName());
        assertEquals(List.of(), result.getRoleNames());
    }

    @Test
    void rejectsDuplicateUsernameWithinCurrentTenant() {
        when(userService.count(any(Wrapper.class))).thenReturn(1L);
        AgentUserCreateReq req = createRequest("agent_zhangsan");

        CheckedException error = assertThrows(CheckedException.class, () -> service.createUser(req));

        assertEquals(400, error.getCode());
        assertEquals("账号已存在", error.getMessage());
        verify(userService, never()).create(any(UserSaveReq.class));
        ArgumentCaptor<LbqWrapper<User>> queryCaptor = wrapperCaptor();
        verify(userService).count(queryCaptor.capture());
        assertExactTenantQuery(queryCaptor.getValue(), "username", "agent_zhangsan");
    }

    @Test
    void readsCreatedUserBackByExactUsernameAndTenant() {
        when(userService.count(any(Wrapper.class))).thenReturn(0L);
        when(orgService.count(any(Wrapper.class))).thenReturn(1L);
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(101L).roleId(21L).build()));

        AgentUserResp result = service.createUser(createRequest("agent_zhangsan"));

        assertEquals(101L, result.getId());
        assertEquals("agent_zhangsan", result.getUsername());
        assertEquals(List.of(21L), result.getRoleIds());
        ArgumentCaptor<UserSaveReq> createCaptor = ArgumentCaptor.forClass(UserSaveReq.class);
        verify(userService).create(createCaptor.capture());
        assertEquals("agent_zhangsan", createCaptor.getValue().getUsername());
        assertNotNull(createCaptor.getValue().getPassword());
        assertFalse(createCaptor.getValue().getPassword().isBlank());
        ArgumentCaptor<LbqWrapper<User>> readBackCaptor = wrapperCaptor();
        verify(userService).getOne(readBackCaptor.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertExactTenantQuery(readBackCaptor.getValue(), "username", "agent_zhangsan");
    }

    @Test
    void replacesUserRolesWithTenantScopedAssignments() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(user);
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(21L).tenantId(TENANT_ID).name("仓库管理员").build(),
                Role.builder().id(22L).tenantId(TENANT_ID).name("审核员").build()));
        when(userRoleService.replaceRolesForUser(101L, List.of(21L, 22L), null))
                .thenReturn(List.of(7L, 8L));

        AgentRoleAssignmentReq req = assignmentRequest(List.of(21L, 22L));
        AgentUserResp result = service.assignRoles(101L, req);

        assertEquals(List.of(21L, 22L), result.getRoleIds());
        assertEquals(List.of(7L, 8L), result.getPreviousRoleIds());
        var ordered = inOrder(operationReceiptMapper, userRoleService);
        ordered.verify(operationReceiptMapper).insert(any(IamAgentOperationReceipt.class));
        ordered.verify(userRoleService).replaceRolesForUser(101L, List.of(21L, 22L), null);
        ordered.verify(operationReceiptMapper).updateById(any(IamAgentOperationReceipt.class));
        ArgumentCaptor<IamAgentOperationReceipt> updateCaptor =
                ArgumentCaptor.forClass(IamAgentOperationReceipt.class);
        verify(operationReceiptMapper).updateById(updateCaptor.capture());
        assertEquals("SUCCEEDED", updateCaptor.getValue().getStatus());
        assertEquals("[7,8]", updateCaptor.getValue().getPreviousRoleIds());
        assertTrue(updateCaptor.getValue().getResponsePayload().contains("\"previousRoleIds\":[7,8]"));
    }

    @Test
    void provisionsUserAndRolesAsOneIdempotentDomainCommand() {
        when(userService.count(any(Wrapper.class))).thenReturn(0L);
        when(orgService.count(any(Wrapper.class))).thenReturn(1L);
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(21L).tenantId(TENANT_ID).name("仓库管理员").build(),
                Role.builder().id(22L).tenantId(TENANT_ID).name("审核员").build()));
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.replaceRolesForUser(101L, List.of(21L, 22L), List.of()))
                .thenReturn(List.of());
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(101L).roleId(21L).build(),
                UserRole.builder().userId(101L).roleId(22L).build()));

        AgentUserResp result = service.provisionUser(AgentUserProvisionReq.builder()
                .operationKey("operation-1").username("agent_zhangsan").nickname("张三")
                .mobile("13800138000").orgId(11L).roleIds(List.of(21L, 22L)).build());

        assertEquals(101L, result.getId());
        assertEquals(List.of(21L, 22L), result.getRoleIds());
        var ordered = inOrder(operationReceiptMapper, userService, userRoleService);
        ordered.verify(operationReceiptMapper).insert(any(IamAgentOperationReceipt.class));
        ordered.verify(userService).create(any(UserSaveReq.class));
        ordered.verify(userService).getOne(any(Wrapper.class), org.mockito.ArgumentMatchers.eq(false));
        ordered.verify(userRoleService).replaceRolesForUser(101L, List.of(21L, 22L), List.of());
        ordered.verify(operationReceiptMapper).updateById(any(IamAgentOperationReceipt.class));
    }

    @Test
    void readsProvisionReceiptBackAgainstCurrentAuthoritativeUserState() {
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder()
                .operationKey("operation-1").operationType("PROVISION_USER").status("SUCCEEDED")
                .userId(101L).tenantId(TENANT_ID)
                .responsePayload("{\"id\":101,\"username\":\"agent_zhangsan\",\"nickname\":\"张三\","
                        + "\"orgId\":11,\"status\":true,\"roleIds\":[21,22]}")
                .build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(
                User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                        .nickname("张三").orgId(11L).status(true).build());
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(101L).roleId(21L).build(),
                UserRole.builder().userId(101L).roleId(22L).build()));

        AgentUserResp result = service.getProvisionResult("operation-1");

        assertEquals(101L, result.getId());
        assertEquals(List.of(21L, 22L), result.getRoleIds());
    }

    @Test
    void rejectsStaleRoleCompensationWithoutOverwritingNewerAssignment() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(user);
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(7L).tenantId(TENANT_ID).name("原角色").build()));
        when(userRoleService.replaceRolesForUser(101L, List.of(7L), List.of(22L)))
                .thenThrow(CheckedException.badRequest(409, "用户角色已被后续操作修改，拒绝覆盖并转人工处理"));
        AgentRoleAssignmentReq compensation = AgentRoleAssignmentReq.builder()
                .operationKey("run-a:compensate-assign-roles@1.0.0")
                .roleIds(List.of(7L)).expectedCurrentRoleIds(List.of(22L)).build();

        CheckedException error = assertThrows(CheckedException.class,
                () -> service.assignRoles(101L, compensation));

        assertEquals(409, error.getCode());
        assertEquals("用户角色已被后续操作修改，拒绝覆盖并转人工处理", error.getMessage());
        verify(userRoleService).replaceRolesForUser(101L, List.of(7L), List.of(22L));
    }

    @Test
    void restoresRolesWhenCurrentAssignmentMatchesCompensationExpectation() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(user);
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(7L).tenantId(TENANT_ID).name("原角色").build()));
        when(userRoleService.replaceRolesForUser(101L, List.of(7L), List.of(22L)))
                .thenReturn(List.of(22L));
        AgentRoleAssignmentReq compensation = AgentRoleAssignmentReq.builder()
                .operationKey("run-a:compensate-assign-roles@1.0.0")
                .roleIds(List.of(7L)).expectedCurrentRoleIds(List.of(22L)).build();

        AgentUserResp result = service.assignRoles(101L, compensation);

        assertEquals(List.of(7L), result.getRoleIds());
        assertEquals(List.of(22L), result.getPreviousRoleIds());
        verify(userRoleService).replaceRolesForUser(101L, List.of(7L), List.of(22L));
    }

    @Test
    void replaysCompletedRoleAssignmentReceiptWithoutReadingOrOverwritingRoles() {
        AgentRoleAssignmentReq req = assignmentRequest(List.of(21L, 22L));
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(601L).tenantId(TENANT_ID)
                .operationKey(req.getOperationKey()).operationType("ASSIGN_ROLES").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(101L, req)).username("agent_zhangsan")
                .userId(101L).previousRoleIds("[7,8]")
                .responsePayload("{\"id\":101,\"username\":\"agent_zhangsan\",\"nickname\":\"张三\","
                        + "\"orgId\":11,\"status\":true,\"roleIds\":[21,22],\"previousRoleIds\":[7,8]}")
                .build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);

        AgentUserResp replayed = service.assignRoles(101L, req);

        assertEquals(List.of(21L, 22L), replayed.getRoleIds());
        assertEquals(List.of(7L, 8L), replayed.getPreviousRoleIds());
        verify(userService, never()).getOne(any(Wrapper.class), anyBoolean());
        verify(roleService, never()).list(any(Wrapper.class));
        verify(userRoleService, never()).list(any(Wrapper.class));
        verify(userRoleService, never()).remove(any(Wrapper.class));
        verify(userRoleService, never()).saveBatch(any());
    }

    @Test
    void rejectsRoleAssignmentWhenOperationKeyRequestDigestConflicts() {
        AgentRoleAssignmentReq original = assignmentRequest(List.of(21L));
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(601L).tenantId(TENANT_ID)
                .operationKey(original.getOperationKey()).operationType("ASSIGN_ROLES").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(101L, original)).username("agent_zhangsan")
                .userId(101L).previousRoleIds("[7]").responsePayload("{}").build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);

        CheckedException error = assertThrows(CheckedException.class,
                () -> service.assignRoles(101L, assignmentRequest(List.of(22L))));

        assertEquals(400, error.getCode());
        assertEquals("幂等操作键与原始请求摘要冲突", error.getMessage());
        verify(userRoleService, never()).remove(any(Wrapper.class));
    }

    @Test
    void duplicateRoleAssignmentReceiptRollsBackWithoutQueryingTheAbortedTransaction() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(user);
        when(roleService.list(any(Wrapper.class))).thenReturn(List.of(
                Role.builder().id(21L).tenantId(TENANT_ID).name("仓库管理员").build()));
        when(operationReceiptMapper.insert(any(IamAgentOperationReceipt.class)))
                .thenThrow(new DuplicateKeyException("并发幂等键冲突"));
        assertThrows(DuplicateKeyException.class,
                () -> service.assignRoles(101L, assignmentRequest(List.of(21L))));

        verify(userRoleService, never()).list(any(Wrapper.class));
        verify(userRoleService, never()).remove(any(Wrapper.class));
        verify(userRoleService, never()).saveBatch(any());
        verify(operationReceiptMapper).selectOne(any(Wrapper.class));
        verify(operationReceiptMapper).insert(any(IamAgentOperationReceipt.class));
        org.mockito.Mockito.verifyNoMoreInteractions(operationReceiptMapper);
    }

    @Test
    void serializesCreateReceiptClaimOnTenantStableUserBeforeReceiptLookup() {
        AgentUserCreateReq req = createRequest("agent_zhangsan");
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(501L).tenantId(TENANT_ID)
                .operationKey(req.getOperationKey()).operationType("CREATE_USER").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(req)).username(req.getUsername()).userId(101L).build();
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username(req.getUsername())
                .nickname("张三").orgId(11L).status(true).build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of());

        service.createUser(req);

        var ordered = inOrder(userService, operationReceiptMapper);
        ArgumentCaptor<LbqWrapper<User>> lockCaptor = wrapperCaptor();
        ordered.verify(userService).list(lockCaptor.capture());
        ordered.verify(operationReceiptMapper).selectOne(any(Wrapper.class));
        assertTenantAgentWriteLock(lockCaptor.getValue());
    }

    @Test
    void serializesRoleReceiptClaimOnTenantStableUserBeforeReceiptLookup() {
        AgentRoleAssignmentReq req = assignmentRequest(List.of(21L));
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(601L).tenantId(TENANT_ID)
                .operationKey(req.getOperationKey()).operationType("ASSIGN_ROLES").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(101L, req)).username("agent_zhangsan")
                .userId(101L).previousRoleIds("[]")
                .responsePayload("{\"id\":101,\"username\":\"agent_zhangsan\",\"nickname\":\"张三\","
                        + "\"orgId\":11,\"status\":true,\"roleIds\":[21],\"previousRoleIds\":[]}")
                .build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);

        service.assignRoles(101L, req);

        var ordered = inOrder(userService, operationReceiptMapper);
        ArgumentCaptor<LbqWrapper<User>> lockCaptor = wrapperCaptor();
        ordered.verify(userService).list(lockCaptor.capture());
        ordered.verify(operationReceiptMapper).selectOne(any(Wrapper.class));
        assertTenantAgentWriteLock(lockCaptor.getValue());
    }

    @Test
    void returnsSuccessfulReceiptWithoutCreatingAnotherUser() {
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(501L).tenantId(TENANT_ID)
                .operationKey("run-1:create-user@1.0.0").operationType("CREATE_USER").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(createRequest("agent_zhangsan")))
                .username("agent_zhangsan").userId(101L).build();
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of());

        AgentUserResp result = service.createUser(createRequest("agent_zhangsan"));

        assertEquals(101L, result.getId());
        verify(userService, never()).create(any(UserSaveReq.class));
        verify(userService, never()).count(any(Wrapper.class));
        ArgumentCaptor<LbqWrapper<User>> readCaptor = wrapperCaptor();
        verify(userService).getOne(readCaptor.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertExactTenantQuery(readCaptor.getValue(), "id", 101L);
    }

    @Test
    void persistsPendingReceiptThenMarksItSucceededAroundOneCreate() {
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        when(operationReceiptMapper.insert(any(IamAgentOperationReceipt.class))).thenReturn(1);
        when(operationReceiptMapper.updateById(any(IamAgentOperationReceipt.class))).thenReturn(1);
        when(userService.count(any(Wrapper.class))).thenReturn(0L);
        when(orgService.count(any(Wrapper.class))).thenReturn(1L);
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of());

        AgentUserResp result = service.createUser(createRequest("agent_zhangsan"));

        assertEquals(101L, result.getId());
        var ordered = inOrder(operationReceiptMapper, userService);
        ordered.verify(operationReceiptMapper).insert(any(IamAgentOperationReceipt.class));
        ordered.verify(userService).create(any(UserSaveReq.class));
        ordered.verify(userService).getOne(any(Wrapper.class), org.mockito.ArgumentMatchers.eq(false));
        ordered.verify(operationReceiptMapper).updateById(any(IamAgentOperationReceipt.class));
        ArgumentCaptor<IamAgentOperationReceipt> insertCaptor =
                ArgumentCaptor.forClass(IamAgentOperationReceipt.class);
        verify(operationReceiptMapper).insert(insertCaptor.capture());
        assertNotNull(insertCaptor.getValue().getLastModifyTime());
        assertEquals(IamAgentRequestDigest.calculate(createRequest("agent_zhangsan")),
                insertCaptor.getValue().getRequestDigest());
        ArgumentCaptor<IamAgentOperationReceipt> updateCaptor =
                ArgumentCaptor.forClass(IamAgentOperationReceipt.class);
        verify(operationReceiptMapper).updateById(updateCaptor.capture());
        assertEquals("SUCCEEDED", updateCaptor.getValue().getStatus());
        assertEquals(101L, updateCaptor.getValue().getUserId());
        assertEquals(TENANT_ID, updateCaptor.getValue().getTenantId());
        assertEquals("run-1:create-user@1.0.0", updateCaptor.getValue().getOperationKey());
    }

    @Test
    void rejectsReusingAnOperationKeyForAnotherUsername() {
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(501L).tenantId(TENANT_ID)
                .operationKey("run-1:create-user@1.0.0").operationType("CREATE_USER").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(createRequest("other_user")))
                .username("other_user").userId(101L).build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);

        CheckedException error = assertThrows(CheckedException.class,
                () -> service.createUser(createRequest("agent_zhangsan")));

        assertEquals(400, error.getCode());
        assertEquals("幂等操作键与原始请求摘要冲突", error.getMessage());
        verify(userService, never()).create(any(UserSaveReq.class));
    }

    @Test
    void rejectsReusingAnOperationKeyWhenTheSemanticRequestDigestDiffers() {
        AgentUserCreateReq original = createRequest("agent_zhangsan");
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(501L).tenantId(TENANT_ID)
                .operationKey(original.getOperationKey()).operationType("CREATE_USER").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(original))
                .username(original.getUsername()).userId(101L).build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(receipt);
        AgentUserCreateReq changed = createRequest("agent_zhangsan");
        changed.setNickname("李四");

        CheckedException error = assertThrows(CheckedException.class, () -> service.createUser(changed));

        assertEquals(400, error.getCode());
        assertEquals("幂等操作键与原始请求摘要冲突", error.getMessage());
        verify(userService, never()).create(any(UserSaveReq.class));
    }

    @Test
    void duplicateReceiptConflictCanBeReconciledOnRetry() {
        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder().id(501L).tenantId(TENANT_ID)
                .operationKey("run-1:create-user@1.0.0").operationType("CREATE_USER").status("SUCCEEDED")
                .requestDigest(IamAgentRequestDigest.calculate(createRequest("agent_zhangsan")))
                .username("agent_zhangsan").userId(101L).build();
        User persisted = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan")
                .nickname("张三").orgId(11L).status(true).build();
        when(operationReceiptMapper.selectOne(any(Wrapper.class))).thenReturn(null, receipt);
        when(operationReceiptMapper.insert(any(IamAgentOperationReceipt.class)))
                .thenThrow(new DuplicateKeyException("并发幂等键冲突"));
        when(userService.count(any(Wrapper.class))).thenReturn(0L);
        when(orgService.count(any(Wrapper.class))).thenReturn(1L);
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(persisted);
        when(userRoleService.list(any(Wrapper.class))).thenReturn(List.of());

        assertThrows(DuplicateKeyException.class, () -> service.createUser(createRequest("agent_zhangsan")));
        AgentUserResp retried = service.createUser(createRequest("agent_zhangsan"));

        assertEquals(101L, retried.getId());
        verify(userService, never()).create(any(UserSaveReq.class));
    }

    @Test
    void deletesOnlyAUserOwnedByTheCurrentTenant() {
        User user = User.builder().id(101L).tenantId(TENANT_ID).username("agent_zhangsan").build();
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(user);

        service.deleteUser(101L);

        ArgumentCaptor<LbqWrapper<User>> queryCaptor = wrapperCaptor();
        verify(userService).getOne(queryCaptor.capture(), org.mockito.ArgumentMatchers.eq(false));
        assertExactTenantQuery(queryCaptor.getValue(), "id", 101L);
        verify(userService).delete(101L);
    }

    @Test
    void treatsDeletingAnAlreadyMissingUserAsIdempotentSuccess() {
        when(userService.getOne(any(Wrapper.class), anyBoolean())).thenReturn(null);

        service.deleteUser(101L);

        verify(userService, never()).delete(any());
    }

    private AgentUserCreateReq createRequest(String username) {
        AgentUserCreateReq req = new AgentUserCreateReq();
        req.setUsername(username);
        req.setOperationKey("run-1:create-user@1.0.0");
        req.setNickname("张三");
        req.setMobile("13800138000");
        req.setEmail("zhangsan@example.com");
        req.setOrgId(11L);
        return req;
    }

    private AgentRoleAssignmentReq assignmentRequest(List<Long> roleIds) {
        return AgentRoleAssignmentReq.builder().operationKey("run-1:assign-roles@1.0.0")
                .roleIds(roleIds).build();
    }

    private void assertExactTenantQuery(LbqWrapper<?> query, String column, Object exactValue) {
        String sql = query.getCustomSqlSegment().toLowerCase();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains(column));
        assertFalse(sql.contains(" like "));
        assertTrue(query.getParamNameValuePairs().containsValue(TENANT_ID));
        assertTrue(query.getParamNameValuePairs().containsValue(exactValue));
    }

    private void assertTenantAgentWriteLock(LbqWrapper<User> query) {
        String sql = query.getCustomSqlSegment().toLowerCase();
        assertTrue(sql.contains("tenant_id"));
        assertTrue(sql.contains("order by"));
        assertTrue(sql.contains("limit 1 for update"));
        assertTrue(query.getParamNameValuePairs().containsValue(TENANT_ID));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<LbqWrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LbqWrapper.class);
    }

    private static void initTableInfo(Class<?> entityType) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
    }

}
