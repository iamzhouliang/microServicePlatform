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

package com.microservice.platform.iam.system.service;

import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserSearchReq;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentOrgResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentRoleResp;
import com.microservice.platform.iam.feign.agent.domain.resp.AgentUserResp;

import java.util.List;

/**
 * IAM 领域持有的受治理智能体操作编排边界。
 *
 * @author OmX
 * @since 2026-07-14
 */
public interface IamAgentService {

    List<AgentUserResp> searchUsers(AgentUserSearchReq req);

    AgentUserResp createUser(AgentUserCreateReq req);

    AgentUserResp provisionUser(AgentUserProvisionReq req);

    /**
     * 按幂等操作键执行权威回读，用于结果未知时对账。
     *
     * @param operationKey Harness 生成的幂等操作键
     * @return 已持久化的权威用户开通结果
     */
    AgentUserResp getProvisionResult(String operationKey);

    void deleteUser(Long userId);

    AgentOrgResp resolveOrg(String name);

    AgentRoleResp resolveRole(String name);

    AgentUserResp assignRoles(Long userId, AgentRoleAssignmentReq req);

}
