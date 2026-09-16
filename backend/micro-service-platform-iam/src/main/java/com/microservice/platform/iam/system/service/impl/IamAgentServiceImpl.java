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

import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
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
import com.microservice.platform.iam.system.domain.enums.Sex;
import com.microservice.platform.iam.system.repository.IamAgentOperationReceiptMapper;
import com.microservice.platform.iam.system.service.IamAgentService;
import com.microservice.platform.iam.system.service.OrgService;
import com.microservice.platform.iam.system.service.RoleService;
import com.microservice.platform.iam.system.service.UserRoleService;
import com.microservice.platform.iam.system.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @author OmX
 * @since 2026-07-14
 */
@Service
@RequiredArgsConstructor
public class IamAgentServiceImpl implements IamAgentService {

    private static final String CREATE_USER_OPERATION = "CREATE_USER";
    private static final String ASSIGN_ROLES_OPERATION = "ASSIGN_ROLES";
    private static final String PROVISION_USER_OPERATION = "PROVISION_USER";
    private static final String OPERATION_PENDING = "PENDING";
    private static final String OPERATION_SUCCEEDED = "SUCCEEDED";
    private static final ObjectMapper RECEIPT_JSON = new ObjectMapper();

    private final AuthenticationContext context;
    private final UserService userService;
    private final OrgService orgService;
    private final RoleService roleService;
    private final UserRoleService userRoleService;
    private final IamAgentOperationReceiptMapper operationReceiptMapper;

    @Override
    public List<AgentUserResp> searchUsers(AgentUserSearchReq req) {
        if (req == null || (req.getUserId() == null
                && allBlank(req.getUsername(), req.getNickname(), req.getMobile(), req.getEmail()))) {
            throw CheckedException.badRequest("至少提供一个用户查询条件");
        }
        LbqWrapper<User> query = Wraps.<User>lbQ().eq(User::getTenantId, tenantId())
                .eq(User::getId, req.getUserId());
        if (Boolean.FALSE.equals(req.getExact())) {
            query.like(User::getUsername, req.getUsername()).like(User::getNickname, req.getNickname())
                    .like(User::getMobile, req.getMobile()).like(User::getEmail, req.getEmail());
        } else {
            query.eq(User::getUsername, req.getUsername()).eq(User::getNickname, req.getNickname())
                    .eq(User::getMobile, req.getMobile()).eq(User::getEmail, req.getEmail());
        }
        return projectUsers(userService.list(query));
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public AgentUserResp createUser(AgentUserCreateReq req) {
        validateCreateRequest(req);
        Long tenantId = tenantId();
        lockTenantAgentWrites(tenantId);
        IamAgentOperationReceipt existingReceipt = findReceipt(tenantId, req.getOperationKey());
        if (existingReceipt != null) {
            return replayCreate(existingReceipt, req, tenantId);
        }
        long duplicateCount = userService.count(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getUsername, req.getUsername()));
        if (duplicateCount > 0) {
            throw CheckedException.badRequest("账号已存在");
        }
        long orgCount = orgService.count(Wraps.<Org>lbQ().eq(Org::getTenantId, tenantId).eq(Org::getId, req.getOrgId()));
        if (orgCount != 1) {
            throw CheckedException.notFound("组织不存在或不属于当前租户");
        }

        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder()
                .operationKey(req.getOperationKey()).operationType(CREATE_USER_OPERATION)
                .status(OPERATION_PENDING).requestDigest(IamAgentRequestDigest.calculate(req))
                .username(req.getUsername()).tenantId(tenantId)
                .lastModifyTime(Instant.now()).build();
        if (operationReceiptMapper.insert(receipt) != 1) {
            throw new IllegalStateException("IAM 智能体操作回执创建失败");
        }

        UserSaveReq createReq = new UserSaveReq();
        createReq.setUsername(req.getUsername());
        createReq.setPassword(IdUtil.fastSimpleUUID());
        createReq.setNickname(req.getNickname());
        createReq.setMobile(req.getMobile());
        createReq.setEmail(req.getEmail());
        createReq.setOrgId(req.getOrgId());
        createReq.setSex(Sex.UNKONW);
        createReq.setStatus(true);
        userService.create(createReq);

        User created = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getUsername, req.getUsername()), false);
        if (created == null) {
            throw CheckedException.notFound("创建后的用户读取失败");
        }
        receipt.setUserId(created.getId());
        receipt.setStatus(OPERATION_SUCCEEDED);
        if (operationReceiptMapper.updateById(receipt) != 1) {
            throw new IllegalStateException("IAM 智能体操作回执更新失败");
        }
        return projectUsers(List.of(created)).getFirst();
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public AgentUserResp provisionUser(AgentUserProvisionReq req) {
        validateProvisionRequest(req);
        Long tenantId = tenantId();
        lockTenantAgentWrites(tenantId);
        IamAgentOperationReceipt existingReceipt = findReceipt(tenantId, req.getOperationKey());
        if (existingReceipt != null) {
            return replayProvision(existingReceipt, req, tenantId);
        }
        validateNewUserDependencies(req, tenantId);

        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder()
                .operationKey(req.getOperationKey()).operationType(PROVISION_USER_OPERATION)
                .status(OPERATION_PENDING).requestDigest(IamAgentRequestDigest.calculate(req))
                .username(req.getUsername()).tenantId(tenantId).lastModifyTime(Instant.now()).build();
        if (operationReceiptMapper.insert(receipt) != 1) {
            throw new IllegalStateException("IAM 原子用户开通回执创建失败");
        }

        UserSaveReq createReq = new UserSaveReq();
        createReq.setUsername(req.getUsername());
        createReq.setPassword(IdUtil.fastSimpleUUID());
        createReq.setNickname(req.getNickname());
        createReq.setMobile(req.getMobile());
        createReq.setEmail(req.getEmail());
        createReq.setOrgId(req.getOrgId());
        createReq.setSex(Sex.UNKONW);
        createReq.setStatus(true);
        userService.create(createReq);

        User created = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getUsername, req.getUsername()), false);
        if (created == null) {
            throw CheckedException.notFound("创建后的用户读取失败，已回滚用户开通");
        }
        List<Long> roleIds = new LinkedHashSet<>(req.getRoleIds()).stream().toList();
        userRoleService.replaceRolesForUser(created.getId(), roleIds, List.of());
        AgentUserResp verified = projectUsers(List.of(created)).getFirst();
        if (!new LinkedHashSet<>(roleIds).equals(new LinkedHashSet<>(verified.getRoleIds()))) {
            throw new IllegalStateException("创建用户并分配角色后的权威回读不一致，已回滚用户开通");
        }

        receipt.setUserId(created.getId());
        receipt.setResponsePayload(writeReceiptJson(verified));
        receipt.setStatus(OPERATION_SUCCEEDED);
        if (operationReceiptMapper.updateById(receipt) != 1) {
            throw new IllegalStateException("IAM 原子用户开通回执更新失败");
        }
        return verified;
    }

    @Override
    public AgentUserResp getProvisionResult(String operationKey) {
        if (StrUtil.isBlank(operationKey)) {
            throw CheckedException.badRequest("幂等操作键不能为空");
        }
        validateOperationKey(operationKey);
        Long tenantId = tenantId();
        IamAgentOperationReceipt receipt = findReceipt(tenantId, operationKey);
        if (receipt == null) {
            throw CheckedException.notFound("未找到用户开通操作回执");
        }
        if (!PROVISION_USER_OPERATION.equals(receipt.getOperationType())) {
            throw CheckedException.badRequest("幂等操作键对应的不是用户开通操作");
        }
        if (!OPERATION_SUCCEEDED.equals(receipt.getStatus()) || receipt.getUserId() == null
                || StrUtil.isBlank(receipt.getResponsePayload())) {
            throw CheckedException.badRequest("用户开通操作尚未形成可确认结果");
        }
        AgentUserResp receiptResult = readReceiptJson(receipt.getResponsePayload(), AgentUserResp.class);
        User current = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getId, receipt.getUserId()), false);
        if (current == null) {
            throw CheckedException.notFound("用户开通回执存在，但权威用户记录不存在");
        }
        AgentUserResp authoritative = projectUsers(List.of(current)).getFirst();
        if (!Objects.equals(receiptResult.getUsername(), authoritative.getUsername())
                || !Objects.equals(receiptResult.getOrgId(), authoritative.getOrgId())
                || !new LinkedHashSet<>(receiptResult.getRoleIds())
                        .equals(new LinkedHashSet<>(authoritative.getRoleIds()))) {
            throw new IllegalStateException("用户开通回执与当前权威用户状态不一致");
        }
        return authoritative;
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void deleteUser(Long userId) {
        if (userId == null) {
            throw CheckedException.badRequest("用户ID不能为空");
        }
        User user = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId())
                .eq(User::getId, userId), false);
        if (user == null) {
            return;
        }
        userService.delete(userId);
    }

    @Override
    public AgentOrgResp resolveOrg(String name) {
        requireName(name, "组织名称不能为空");
        List<Org> matches = orgService.list(Wraps.<Org>lbQ().eq(Org::getTenantId, tenantId())
                .and(query -> query.eq(Org::getLabel, name).or().eq(Org::getAlias, name)));
        Org org = requireSingle(matches, "组织不存在", "组织名称不唯一");
        return AgentOrgResp.builder().id(org.getId()).name(org.getLabel()).alias(org.getAlias()).build();
    }

    @Override
    public AgentRoleResp resolveRole(String name) {
        requireName(name, "角色名称不能为空");
        List<Role> matches = roleService.list(Wraps.<Role>lbQ().eq(Role::getTenantId, tenantId())
                .and(query -> query.eq(Role::getName, name).or().eq(Role::getCode, name)));
        Role role = requireSingle(matches, "角色不存在", "角色名称或编码不唯一");
        return AgentRoleResp.builder().id(role.getId()).name(role.getName()).code(role.getCode())
                .superRole(role.getSuperRole()).build();
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public AgentUserResp assignRoles(Long userId, AgentRoleAssignmentReq req) {
        validateRoleAssignmentRequest(userId, req);
        Long tenantId = tenantId();
        lockTenantAgentWrites(tenantId);
        IamAgentOperationReceipt existingReceipt = findReceipt(tenantId, req.getOperationKey());
        if (existingReceipt != null) {
            return replayRoleAssignment(existingReceipt, userId, req);
        }
        User user = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getId, userId), false);
        if (user == null) {
            throw CheckedException.notFound("用户不存在或不属于当前租户");
        }

        List<Long> roleIds = new LinkedHashSet<>(req.getRoleIds()).stream().toList();
        List<Role> roles = Collections.emptyList();
        if (!roleIds.isEmpty()) {
            roles = roleService.list(Wraps.<Role>lbQ().eq(Role::getTenantId, tenantId).in(Role::getId, roleIds));
            if (roles.size() != roleIds.size()) {
                throw CheckedException.notFound("角色不存在或不属于当前租户");
            }
        }

        IamAgentOperationReceipt receipt = IamAgentOperationReceipt.builder()
                .operationKey(req.getOperationKey()).operationType(ASSIGN_ROLES_OPERATION)
                .status(OPERATION_PENDING).requestDigest(IamAgentRequestDigest.calculate(userId, req))
                .username(user.getUsername()).userId(userId).tenantId(tenantId)
                .lastModifyTime(Instant.now()).build();
        if (operationReceiptMapper.insert(receipt) != 1) {
            throw new IllegalStateException("IAM 智能体操作回执创建失败");
        }

        List<Long> previousRoleIds = userRoleService.replaceRolesForUser(userId, roleIds,
                req.getExpectedCurrentRoleIds());
        Map<Long, String> orgNamesById = loadOrgNames(tenantId, List.of(user));
        Map<Long, String> roleNamesById = roles.stream().collect(Collectors.toMap(
                Role::getId, Role::getName, (left, right) -> left));
        AgentUserResp response = toResponse(user, roleIds, orgNamesById, roleNamesById);
        response.setPreviousRoleIds(List.copyOf(previousRoleIds));
        receipt.setPreviousRoleIds(writeReceiptJson(previousRoleIds));
        receipt.setResponsePayload(writeReceiptJson(response));
        receipt.setStatus(OPERATION_SUCCEEDED);
        if (operationReceiptMapper.updateById(receipt) != 1) {
            throw new IllegalStateException("IAM 智能体操作回执更新失败");
        }
        return response;
    }

    private List<AgentUserResp> projectUsers(List<User> users) {
        if (users == null || users.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> userIds = users.stream().map(User::getId).toList();
        Map<Long, List<Long>> rolesByUser = userRoleService.list(Wraps.<UserRole>lbQ().in(UserRole::getUserId, userIds))
                .stream().collect(Collectors.groupingBy(UserRole::getUserId,
                        Collectors.mapping(UserRole::getRoleId, Collectors.toList())));
        Long tenantId = tenantId();
        Map<Long, String> orgNamesById = loadOrgNames(tenantId, users);
        List<Long> roleIds = rolesByUser.values().stream().flatMap(List::stream).distinct().toList();
        Map<Long, String> roleNamesById = roleIds.isEmpty()
                ? Map.of()
                : roleService.list(Wraps.<Role>lbQ().eq(Role::getTenantId, tenantId).in(Role::getId, roleIds))
                        .stream().collect(Collectors.toMap(Role::getId, Role::getName, (left, right) -> left));
        return users.stream().map(user -> toResponse(user,
                rolesByUser.getOrDefault(user.getId(), List.of()), orgNamesById, roleNamesById)).toList();
    }

    private Map<Long, String> loadOrgNames(Long tenantId, List<User> users) {
        List<Long> orgIds = users.stream().map(User::getOrgId).filter(Objects::nonNull).distinct().toList();
        if (orgIds.isEmpty()) {
            return Map.of();
        }
        return orgService.list(Wraps.<Org>lbQ().eq(Org::getTenantId, tenantId).in(Org::getId, orgIds))
                .stream().collect(Collectors.toMap(Org::getId, Org::getLabel, (left, right) -> left));
    }

    private AgentUserResp toResponse(User user, List<Long> roleIds, Map<Long, String> orgNamesById,
                                     Map<Long, String> roleNamesById) {
        List<String> roleNames = roleIds.stream().map(roleNamesById::get).filter(Objects::nonNull).toList();
        String orgName = user.getOrgId() == null ? null : orgNamesById.get(user.getOrgId());
        return AgentUserResp.builder().id(user.getId()).username(user.getUsername()).nickname(user.getNickname())
                .orgId(user.getOrgId()).orgName(orgName).status(user.getStatus())
                .roleIds(List.copyOf(roleIds)).roleNames(roleNames).build();
    }

    private Long tenantId() {
        Long tenantId = context.tenantId();
        if (tenantId == null) {
            throw CheckedException.forbidden();
        }
        return tenantId;
    }

    private void validateCreateRequest(AgentUserCreateReq req) {
        if (req == null || StrUtil.isBlank(req.getOperationKey()) || StrUtil.isBlank(req.getUsername())
                || StrUtil.isBlank(req.getNickname()) || req.getOrgId() == null) {
            throw CheckedException.badRequest("幂等操作键、用户名、昵称和组织ID不能为空");
        }
        validateOperationKey(req.getOperationKey());
    }

    private void validateProvisionRequest(AgentUserProvisionReq req) {
        if (req == null || StrUtil.isBlank(req.getOperationKey()) || StrUtil.isBlank(req.getUsername())
                || StrUtil.isBlank(req.getNickname()) || req.getOrgId() == null || req.getRoleIds() == null) {
            throw CheckedException.badRequest("幂等操作键、用户名、昵称、组织ID和角色ID列表不能为空");
        }
        if (req.getOrgId() <= 0 || containsInvalidRoleId(req.getRoleIds())) {
            throw CheckedException.badRequest("组织ID和角色ID必须为正整数");
        }
        validateOperationKey(req.getOperationKey());
    }

    private void validateNewUserDependencies(AgentUserProvisionReq req, Long tenantId) {
        long duplicateCount = userService.count(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getUsername, req.getUsername()));
        if (duplicateCount > 0) {
            throw CheckedException.badRequest("账号已存在");
        }
        long orgCount = orgService.count(Wraps.<Org>lbQ().eq(Org::getTenantId, tenantId)
                .eq(Org::getId, req.getOrgId()));
        if (orgCount != 1) {
            throw CheckedException.notFound("组织不存在或不属于当前租户");
        }
        List<Long> roleIds = new LinkedHashSet<>(req.getRoleIds()).stream().toList();
        if (!roleIds.isEmpty()) {
            List<Role> roles = roleService.list(Wraps.<Role>lbQ().eq(Role::getTenantId, tenantId)
                    .in(Role::getId, roleIds));
            if (roles.size() != roleIds.size()) {
                throw CheckedException.notFound("角色不存在或不属于当前租户");
            }
        }
    }

    private AgentUserResp replayProvision(IamAgentOperationReceipt receipt, AgentUserProvisionReq req,
                                          Long tenantId) {
        if (!PROVISION_USER_OPERATION.equals(receipt.getOperationType())) {
            throw CheckedException.badRequest("幂等操作键与原始操作类型不匹配");
        }
        if (!IamAgentRequestDigest.calculate(req).equals(receipt.getRequestDigest())) {
            throw CheckedException.badRequest("幂等操作键与原始请求摘要冲突");
        }
        if (!OPERATION_SUCCEEDED.equals(receipt.getStatus()) || receipt.getUserId() == null) {
            throw CheckedException.badRequest("幂等用户开通尚未完成，请人工核验后再试");
        }
        User user = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getId, receipt.getUserId()), false);
        if (user == null || !req.getUsername().equals(user.getUsername())) {
            throw CheckedException.notFound("幂等用户开通关联用户不存在，请人工处理");
        }
        AgentUserResp verified = projectUsers(List.of(user)).getFirst();
        if (!new LinkedHashSet<>(req.getRoleIds()).equals(new LinkedHashSet<>(verified.getRoleIds()))) {
            throw CheckedException.notFound("幂等用户开通角色关系与原请求不一致，请人工处理");
        }
        return verified;
    }

    private void validateRoleAssignmentRequest(Long userId, AgentRoleAssignmentReq req) {
        if (userId == null || req == null || StrUtil.isBlank(req.getOperationKey()) || req.getRoleIds() == null) {
            throw CheckedException.badRequest("幂等操作键、用户ID和角色ID列表不能为空");
        }
        if (userId <= 0 || containsInvalidRoleId(req.getRoleIds())
                || (req.getExpectedCurrentRoleIds() != null && containsInvalidRoleId(req.getExpectedCurrentRoleIds()))) {
            throw CheckedException.badRequest("用户ID和角色ID必须为正整数");
        }
        validateOperationKey(req.getOperationKey());
    }

    private void validateOperationKey(String operationKey) {
        if (operationKey.length() > 128 || !operationKey.matches("^[A-Za-z0-9._:@-]+$")) {
            throw CheckedException.badRequest("幂等操作键格式错误");
        }
    }

    private boolean containsInvalidRoleId(List<Long> roleIds) {
        return roleIds.stream().anyMatch(roleId -> roleId == null || roleId <= 0);
    }

    private IamAgentOperationReceipt findReceipt(Long tenantId, String operationKey) {
        return operationReceiptMapper.selectOne(Wraps.<IamAgentOperationReceipt>lbQ()
                .eq(IamAgentOperationReceipt::getTenantId, tenantId)
                .eq(IamAgentOperationReceipt::getOperationKey, operationKey));
    }

    /**
     * 同一租户的智能体写操作先锁定固定用户行，使回执检查与创建在 MySQL、PostgreSQL 下都按租户串行。
     *
     * @param tenantId 当前租户 ID
     * @throws IllegalStateException 当前租户不存在可加锁用户时抛出
     */
    private void lockTenantAgentWrites(Long tenantId) {
        List<User> lockedUsers = userService.list(Wraps.<User>lbQ()
                .select(User::getId)
                .eq(User::getTenantId, tenantId)
                .orderByAsc(User::getId)
                .last("LIMIT 1 FOR UPDATE"));
        if (lockedUsers.isEmpty()) {
            throw new IllegalStateException("当前租户缺少可用于串行化智能体写操作的用户");
        }
    }

    private AgentUserResp replayCreate(IamAgentOperationReceipt receipt, AgentUserCreateReq req, Long tenantId) {
        if (!CREATE_USER_OPERATION.equals(receipt.getOperationType())) {
            throw CheckedException.badRequest("幂等操作键与原始操作类型不匹配");
        }
        if (!IamAgentRequestDigest.calculate(req).equals(receipt.getRequestDigest())) {
            throw CheckedException.badRequest("幂等操作键与原始请求摘要冲突");
        }
        if (!OPERATION_SUCCEEDED.equals(receipt.getStatus())) {
            throw CheckedException.badRequest("幂等操作尚未完成，请稍后重试");
        }
        if (receipt.getUserId() == null) {
            throw CheckedException.notFound("幂等操作回执缺少用户ID，请人工处理");
        }
        User user = userService.getOne(Wraps.<User>lbQ().eq(User::getTenantId, tenantId)
                .eq(User::getId, receipt.getUserId()), false);
        if (user == null || !receipt.getUsername().equals(user.getUsername())) {
            throw CheckedException.notFound("幂等操作关联用户不存在，请人工处理");
        }
        return projectUsers(List.of(user)).getFirst();
    }

    private AgentUserResp replayRoleAssignment(IamAgentOperationReceipt receipt, Long userId,
                                               AgentRoleAssignmentReq req) {
        if (!ASSIGN_ROLES_OPERATION.equals(receipt.getOperationType())) {
            throw CheckedException.badRequest("幂等操作键与原始操作类型不匹配");
        }
        if (!IamAgentRequestDigest.calculate(userId, req).equals(receipt.getRequestDigest())) {
            throw CheckedException.badRequest("幂等操作键与原始请求摘要冲突");
        }
        if (!OPERATION_SUCCEEDED.equals(receipt.getStatus())) {
            throw CheckedException.badRequest("幂等操作尚未完成，请稍后重试");
        }
        if (!userId.equals(receipt.getUserId()) || StrUtil.isBlank(receipt.getPreviousRoleIds())
                || StrUtil.isBlank(receipt.getResponsePayload())) {
            throw CheckedException.notFound("幂等操作回执数据不完整，请人工处理");
        }
        AgentUserResp response = readReceiptJson(receipt.getResponsePayload(), AgentUserResp.class);
        List<Long> previousRoleIds = readReceiptJson(receipt.getPreviousRoleIds(), new TypeReference<>() {
        });
        if (response == null || !userId.equals(response.getId())
                || !previousRoleIds.equals(response.getPreviousRoleIds())) {
            throw CheckedException.notFound("幂等操作回执响应无效，请人工处理");
        }
        return response;
    }

    private String writeReceiptJson(Object value) {
        try {
            return RECEIPT_JSON.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM 智能体操作回执序列化失败", exception);
        }
    }

    private <T> T readReceiptJson(String value, Class<T> type) {
        try {
            return RECEIPT_JSON.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM 智能体操作回执反序列化失败", exception);
        }
    }

    private <T> T readReceiptJson(String value, TypeReference<T> type) {
        try {
            return RECEIPT_JSON.readValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("IAM 智能体操作回执反序列化失败", exception);
        }
    }

    private void requireName(String name, String message) {
        if (StrUtil.isBlank(name)) {
            throw CheckedException.badRequest(message);
        }
    }

    private boolean allBlank(String... values) {
        for (String value : values) {
            if (StrUtil.isNotBlank(value)) {
                return false;
            }
        }
        return true;
    }

    private <T> T requireSingle(List<T> matches, String missingMessage, String ambiguousMessage) {
        if (matches == null || matches.isEmpty()) {
            throw CheckedException.notFound(missingMessage);
        }
        if (matches.size() > 1) {
            throw CheckedException.badRequest(ambiguousMessage);
        }
        return matches.getFirst();
    }

}
