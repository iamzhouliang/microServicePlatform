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

package com.microservice.platform.iam.system.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.annotation.SaMode;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserSearchReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentOrgResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentRoleResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;
import com.microservice.platform.iam.system.service.IamAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 受治理智能体工作流使用的 IAM 操作入口。
 *
 * @author xJh
 * @since 2026-07-14
 */
@Validated
@RestController
@RequiredArgsConstructor
@Tag(name = "IAM Agent", description = "Agent 调用的 IAM 权威边界")
public class IamAgentController implements IamAgentFeign {

    private final IamAgentService iamAgentService;

    @Override
    @Operation(summary = "Agent 用户查询")
    @SaCheckPermission(value = {"sys:user:page"})
    public List<AgentUserResp> searchUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                           @Valid @RequestBody AgentUserSearchReq req) {
        return iamAgentService.searchUsers(req);
    }

    @Override
    @AccessLog(module = "IAM Agent", description = "Agent 创建用户")
    @Operation(summary = "Agent 创建用户")
    @SaCheckPermission(value = {"sys:user:add"})
    public AgentUserResp createUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @Valid @RequestBody AgentUserCreateReq req) {
        return iamAgentService.createUser(req);
    }

    @Override
    @AccessLog(module = "IAM Agent", description = "Agent 创建用户并分配角色")
    @Operation(summary = "Agent 创建用户并分配角色")
    @SaCheckPermission(value = {"sys:user:add", "sys:role:assign-users"}, mode = SaMode.AND)
    public AgentUserResp provisionUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                       @Valid @RequestBody AgentUserProvisionReq req) {
        return iamAgentService.provisionUser(req);
    }

    @Override
    @Operation(summary = "Agent 用户开通结果权威回读")
    @SaCheckPermission(value = {"sys:user:add", "sys:role:assign-users"}, mode = SaMode.AND)
    public AgentUserResp getProvisionResult(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                            String operationKey) {
        return iamAgentService.getProvisionResult(operationKey);
    }

    @Override
    @AccessLog(module = "IAM Agent", description = "Agent 删除用户")
    @Operation(summary = "Agent 删除用户")
    @SaCheckPermission(value = {"sys:user:remove"})
    public void deleteUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, Long userId) {
        iamAgentService.deleteUser(userId);
    }

    @Override
    @Operation(summary = "Agent 精确解析组织")
    @SaCheckPermission(value = {"sys:user:add"})
    public AgentOrgResp resolveOrg(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, String name) {
        return iamAgentService.resolveOrg(name);
    }

    @Override
    @Operation(summary = "Agent 精确解析角色")
    @SaCheckPermission(value = {"sys:role:assign-users"})
    public AgentRoleResp resolveRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, String name) {
        return iamAgentService.resolveRole(name);
    }

    @Override
    @AccessLog(module = "IAM Agent", description = "Agent 分配用户角色")
    @Operation(summary = "Agent 分配用户角色")
    @SaCheckPermission(value = {"sys:role:assign-users"})
    public AgentUserResp assignRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization, Long userId,
                                     @Valid @RequestBody AgentRoleAssignmentReq req) {
        return iamAgentService.assignRoles(userId, req);
    }

}
