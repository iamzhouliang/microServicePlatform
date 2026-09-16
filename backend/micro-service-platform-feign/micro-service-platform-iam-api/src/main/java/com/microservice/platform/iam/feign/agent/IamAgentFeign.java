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

package com.microservice.platform.iam.feign.agent;

import com.microservice.framework.commons.FeignConstants;
import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserSearchReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentOrgResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentRoleResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;
import jakarta.validation.Valid;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 面向受治理智能体工作流的 IAM 操作接口。
 *
 * @author OmX
 * @since 2026-07-14
 */
@FeignClient(name = FeignConstants.AUTH_FEIGN_NAME, contextId = "iamAgentFeign", dismiss404 = true)
public interface IamAgentFeign {

    @PostMapping("/agent/iam/users/search")
    List<AgentUserResp> searchUsers(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                    @Valid @RequestBody AgentUserSearchReq req);

    @PostMapping("/agent/iam/users")
    AgentUserResp createUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                             @Valid @RequestBody AgentUserCreateReq req);

    @PostMapping("/agent/iam/user-provisions")
    AgentUserResp provisionUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                @Valid @RequestBody AgentUserProvisionReq req);

    @GetMapping("/agent/iam/user-provisions/{operationKey}/result")
    AgentUserResp getProvisionResult(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                     @PathVariable("operationKey") String operationKey);

    @DeleteMapping("/agent/iam/users/{userId}")
    void deleteUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                    @PathVariable("userId") Long userId);

    @GetMapping("/agent/iam/orgs/resolve")
    AgentOrgResp resolveOrg(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                            @RequestParam("name") String name);

    @GetMapping("/agent/iam/roles/resolve")
    AgentRoleResp resolveRole(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                              @RequestParam("name") String name);

    @PutMapping("/agent/iam/users/{userId}/roles")
    AgentUserResp assignRoles(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                              @PathVariable("userId") Long userId,
                              @Valid @RequestBody AgentRoleAssignmentReq req);

}
