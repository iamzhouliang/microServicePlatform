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

import cn.dev33.satoken.stp.StpUtil;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.UncertainToolExecutionException;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserSearchReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentOrgResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentRoleResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.invocation.InvocationParameters;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * IAM 领域可由 LangChain4j 直接发现和执行的真实工具 Bean。
 *
 * @author xJh
 * @since 2026-07-15
 */
@Service
public final class IamAgentToolService {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IamAgentFeign feign;
    private final Supplier<String> tokenSupplier;

    /** Spring 运行时构造器，授权信息取自当前 Sa-Token 上下文。 */
    @Autowired
    public IamAgentToolService(IamAgentFeign feign) {
        this(feign, IamAgentToolService::currentToken);
    }

    /**
     * 可注入授权信息来源的构造器，供聚焦测试和非 Spring 场景使用。
     *
     * @param feign IAM 智能体接口
     * @param tokenSupplier 当前委托授权信息来源
     */
    public IamAgentToolService(IamAgentFeign feign, Supplier<String> tokenSupplier) {
        this.feign = Objects.requireNonNull(feign, "feign 不能为空");
        this.tokenSupplier = Objects.requireNonNull(tokenSupplier, "tokenSupplier 不能为空");
    }

    private static String currentToken() {
        return StpUtil.getTokenValue();
    }

    @Tool(name = IamAgentTools.RESOLVE_ORG, value = "按组织名称精确解析组织，返回 id 和 name")
    public Map<String, Object> resolveOrg(InvocationParameters parameters, @P("组织名称") String name) {
        AgentOrgResp org = feign.resolveOrg(authorization(parameters), requiredText(name, "name"));
        return Map.of("id", requiredIdText(org.getId(), "组织 ID"), "name", org.getName());
    }

    @Tool(name = IamAgentTools.RESOLVE_ROLE, value = "按角色名称或编码精确解析角色，返回 id、name 和 code")
    public Map<String, Object> resolveRole(InvocationParameters parameters, @P("角色名称或编码") String name) {
        AgentRoleResp role = feign.resolveRole(authorization(parameters), requiredText(name, "name"));
        return Map.of("id", requiredIdText(role.getId(), "角色 ID"), "name", role.getName(),
                "code", role.getCode(), "superRole", Boolean.TRUE.equals(role.getSuperRole()));
    }

    @Tool(name = IamAgentTools.SEARCH_USER,
            value = "按账号、昵称、手机号或邮箱查询用户，返回匹配数量和脱敏用户列表；向用户表达组织和角色时只使用名称")
    public Map<String, Object> searchUser(InvocationParameters parameters,
                                          @P(value = "用户账号", required = false) String username,
                                          @P(value = "用户昵称", required = false) String nickname,
                                          @P(value = "手机号", required = false) String mobile,
                                          @P(value = "邮箱", required = false) String email) {
        AgentUserSearchReq request = searchRequest(null, username, nickname, mobile, email);
        List<AgentUserResp> users = feign.searchUsers(authorization(parameters), request);
        List<Map<String, Object>> redactedUsers = users.stream().map(IamAgentToolService::userData).toList();
        return Map.of("count", users.size(), "users", redactedUsers);
    }

    @Tool(name = IamAgentTools.VERIFY_USER,
            value = "权威核验用户状态。校验 create-user 时必须传绑定创建结果 $.id 的 userId、"
                    + "与写步骤完全一致的 username、与写步骤完全一致的 orgId；校验 assign-roles 时必须传"
                    + "与写步骤完全一致的 userId 和 roleIds；校验 delete-user 时必须传相同 userId 且"
                    + "expectPresent=false；作为 verifier 时 outputBindings 必须为空。返回 verified 和 userId")
    public Map<String, Object> verifyUser(InvocationParameters parameters,
                                          @P(value = "用户 ID；校验 create-user 时必须引用写步骤绑定的 $.id，校验 assign-roles 或 delete-user 时必须与写步骤相同",
                                                  required = false) String userId,
                                          @P(value = "用户账号；校验 create-user 时必须与写步骤 username 完全一致", required = false) String username,
                                          @P(value = "组织 ID；校验 create-user 时必须与写步骤 orgId 完全一致", required = false) String orgId,
                                          @P(value = "角色 ID 列表；校验 assign-roles 时必须与写步骤 roleIds 完全一致", required = false) List<String> roleIds,
                                          @P(value = "预期用户存在；校验 delete-user 时必须传 false", required = false) Boolean expectPresent) {
        Long expectedUserId = optionalPositiveLong(userId, "userId");
        String expectedUsername = optionalText(username, "username");
        Long expectedOrgId = optionalPositiveLong(orgId, "orgId");
        List<Long> expectedRoleIds = optionalToolIdList(roleIds, "roleIds");
        boolean expectedPresent = !Boolean.FALSE.equals(expectPresent);
        if (expectedUserId == null && expectedUsername == null) {
            throw new IllegalArgumentException("用户校验至少需要 userId 或 username");
        }
        List<AgentUserResp> users = feign.searchUsers(authorization(parameters),
                searchRequest(expectedUserId, expectedUsername, null, null, null));
        AgentUserResp user = users.size() == 1 ? users.getFirst() : null;

        Map<String, Object> expected = expectedState(expectedUserId, expectedUsername, expectedOrgId,
                expectedRoleIds);
        Map<String, Object> actual = user == null ? Map.of() : actualState(user);
        boolean stateMatches = user != null
                && (expectedUserId == null || Objects.equals(expectedUserId, user.getId()))
                && (expectedUsername == null || Objects.equals(expectedUsername, user.getUsername()))
                && (expectedOrgId == null || Objects.equals(expectedOrgId, user.getOrgId()))
                && (expectedRoleIds == null || sameIds(expectedRoleIds, user.getRoleIds()));
        boolean verified = expectedPresent ? stateMatches : users.isEmpty();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("verified", verified);
        result.put("count", users.size());
        result.put("expectedPresent", expectedPresent);
        result.put("expected", expected);
        result.put("actual", actual);
        if (user != null && user.getId() != null) {
            result.put("userId", requiredIdText(user.getId(), "用户 ID"));
        }
        if (!verified) {
            throw new IllegalStateException("IAM 用户回读结果与预期状态不一致");
        }
        return Map.copyOf(result);
    }

    @Tool(name = IamAgentTools.PROVISION_USER,
            value = "在同一 IAM 事务中创建用户并分配角色。缺少账号、姓名、手机号、组织 ID 或角色 ID 时先向用户追问，不得猜测")
    public Map<String, Object> provisionUser(InvocationParameters parameters, @P("用户账号") String username,
                                             @P("用户昵称") String nickname, @P("手机号") String mobile,
                                             @P(value = "邮箱", required = false) String email, @P("组织 ID") String orgId,
                                             @P("角色 ID 列表") List<String> roleIds) {
        HarnessInvocation invocation = HarnessInvocation.from(parameters)
                .orElseThrow(() -> new IllegalArgumentException("创建用户缺少可信调用身份"));
        AgentUserProvisionReq request = AgentUserProvisionReq.builder()
                .operationKey(invocation.operationId())
                .username(requiredText(username, "username"))
                .nickname(requiredText(nickname, "nickname"))
                .mobile(requiredText(mobile, "mobile"))
                .email(optionalText(email, "email"))
                .orgId(requiredPositiveLong(orgId, "orgId"))
                .roleIds(requiredToolIdListAllowEmpty(roleIds, "roleIds"))
                .build();
        String authorization = authorization(parameters);
        try {
            return userData(feign.provisionUser(authorization, request));
        } catch (RuntimeException failure) {
            if (!executionResultMayBeUnknown(failure)) {
                throw failure;
            }
            try {
                return userData(feign.getProvisionResult(authorization, invocation.operationId()));
            } catch (RuntimeException verificationFailure) {
                throw new UncertainToolExecutionException(
                        "IAM 用户开通请求已发出，但权威回读仍无法确认结果，禁止自动重试",
                        verificationFailure);
            }
        }
    }

    private String authorization(InvocationParameters parameters) {
        String authorization = DelegatedAuthorization.from(parameters)
                .map(DelegatedAuthorization::token).orElseGet(tokenSupplier);
        if (authorization == null || authorization.isBlank()) {
            throw new IllegalStateException("委托授权信息不可用");
        }
        String normalized = authorization.trim();
        if (normalized.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            String token = normalized.substring(BEARER_PREFIX.length()).trim();
            if (token.isEmpty()) {
                throw new IllegalStateException("委托授权信息不可用");
            }
            return BEARER_PREFIX + token;
        }
        return BEARER_PREFIX + normalized;
    }

    private static AgentUserSearchReq searchRequest(Long userId, String username, String nickname, String mobile,
                                                    String email) {
        return AgentUserSearchReq.builder()
                .userId(userId)
                .username(optionalText(username, "username"))
                .nickname(optionalText(nickname, "nickname"))
                .mobile(optionalText(mobile, "mobile"))
                .email(optionalText(email, "email"))
                .exact(true)
                .build();
    }

    private static Map<String, Object> expectedState(Long userId, String username, Long orgId, List<Long> roleIds) {
        Map<String, Object> expected = new LinkedHashMap<>();
        putIdIfNotNull(expected, "userId", userId);
        putIfNotNull(expected, "username", username);
        putIdIfNotNull(expected, "orgId", orgId);
        if (roleIds != null) {
            expected.put("roleIds", externalIdList(roleIds, "roleIds"));
        }
        return Map.copyOf(expected);
    }

    private static Map<String, Object> actualState(AgentUserResp user) {
        Map<String, Object> actual = new LinkedHashMap<>();
        putIfNotNull(actual, "username", user.getUsername());
        putIdIfNotNull(actual, "orgId", user.getOrgId());
        actual.put("roleIds", externalIdList(user.getRoleIds(), "roleIds"));
        return Map.copyOf(actual);
    }

    private static Map<String, Object> userData(AgentUserResp user) {
        Map<String, Object> data = new LinkedHashMap<>();
        putIdIfNotNull(data, "id", user.getId());
        putIfNotNull(data, "username", user.getUsername());
        putIfNotNull(data, "nickname", user.getNickname());
        data.put("orgName", readableOrgName(user));
        putIfNotNull(data, "status", user.getStatus());
        data.put("roleNames", readableRoleNames(user));
        return Map.copyOf(data);
    }

    private static String readableOrgName(AgentUserResp user) {
        if (user.getOrgName() != null && !user.getOrgName().isBlank()) {
            return user.getOrgName().trim();
        }
        return user.getOrgId() == null ? "未分配组织" : "组织信息不可用";
    }

    private static List<String> readableRoleNames(AgentUserResp user) {
        if (user.getRoleNames() != null && !user.getRoleNames().isEmpty()) {
            return user.getRoleNames();
        }
        return user.getRoleIds() == null || user.getRoleIds().isEmpty()
                ? List.of("未分配角色")
                : List.of("角色信息不可用");
    }

    private static String requiredText(String value, String name) {
        String result = optionalText(value, name);
        if (result == null) {
            throw new IllegalArgumentException("缺少必填工具输入: " + name);
        }
        return result;
    }

    private static String optionalText(String value, String name) {
        if (value == null) {
            return null;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException("工具输入必须是非空文本: " + name);
        }
        return value.trim();
    }

    private static Long requiredPositiveLong(String value, String name) {
        return parsePositiveLong(value, name);
    }

    private static Long optionalPositiveLong(String value, String name) {
        return value == null ? null : requiredPositiveLong(value, name);
    }

    private static List<Long> requiredToolIdListAllowEmpty(List<String> values, String name) {
        List<Long> result = optionalToolIdList(values, name);
        if (result == null) {
            throw new IllegalArgumentException("缺少必填列表工具输入: " + name);
        }
        return result;
    }

    private static List<Long> optionalToolIdList(List<String> values, String name) {
        if (values == null) {
            return null;
        }
        return values.stream().map(value -> requiredPositiveLong(value, name)).toList();
    }

    private static String requiredIdText(Object value, String name) {
        return Long.toString(parsePositiveLong(value, name));
    }

    private static List<String> externalIdList(List<Long> values, String name) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(value -> requiredIdText(value, name)).toList();
    }

    private static boolean sameIds(List<Long> expected, List<Long> actual) {
        return new LinkedHashSet<>(expected).equals(new LinkedHashSet<>(actual == null ? List.of() : actual));
    }

    private static boolean executionResultMayBeUnknown(RuntimeException failure) {
        if (!(failure instanceof FeignException feignFailure)) {
            return false;
        }
        int status = feignFailure.status();
        return status < 0 || status == 408 || status >= 500;
    }

    private static void putIfNotNull(Map<String, Object> target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    private static void putIdIfNotNull(Map<String, Object> target, String key, Long value) {
        if (value != null) {
            target.put(key, requiredIdText(value, key));
        }
    }

    private static Long parsePositiveLong(Object value, String name) {
        try {
            long id = Long.parseLong(String.valueOf(value));
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("工具输入必须是正整数: " + name, failure);
        }
    }
}
