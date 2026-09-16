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

import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IAM 智能体创建用户请求摘要测试。
 *
 * @author OmX
 */
class IamAgentRequestDigestTest {
    
    @Test
    void producesStableSha256DigestForTheSameSemanticRequest() {
        String first = IamAgentRequestDigest.calculate(createRequest());
        String second = IamAgentRequestDigest.calculate(createRequest());
        
        assertEquals(first, second);
        assertEquals(64, first.length());
        assertTrue(first.matches("^[0-9a-f]{64}$"));
    }
    
    @Test
    void changesDigestWhenAnySemanticFieldChanges() {
        String original = IamAgentRequestDigest.calculate(createRequest());
        List<Consumer<AgentUserCreateReq>> mutations = List.of(
                req -> req.setUsername("agent_lisi"),
                req -> req.setNickname("李四"),
                req -> req.setMobile("13900139000"),
                req -> req.setEmail("lisi@example.com"),
                req -> req.setOrgId(12L));
        
        for (Consumer<AgentUserCreateReq> mutation : mutations) {
            AgentUserCreateReq changed = createRequest();
            mutation.accept(changed);
            assertNotEquals(original, IamAgentRequestDigest.calculate(changed));
        }
    }
    
    @Test
    void roleAssignmentDigestNormalizesDuplicateAndReorderedRoleIds() {
        AgentRoleAssignmentReq original = AgentRoleAssignmentReq.builder()
                .operationKey("run-1:assign-roles@1.0.0").roleIds(List.of(22L, 21L, 22L)).build();
        AgentRoleAssignmentReq equivalent = AgentRoleAssignmentReq.builder()
                .operationKey("another-key").roleIds(List.of(21L, 22L)).build();
        AgentRoleAssignmentReq changed = AgentRoleAssignmentReq.builder()
                .operationKey(original.getOperationKey()).roleIds(List.of(21L, 23L)).build();
        
        assertEquals(IamAgentRequestDigest.calculate(101L, original),
                IamAgentRequestDigest.calculate(101L, equivalent));
        assertNotEquals(IamAgentRequestDigest.calculate(101L, original),
                IamAgentRequestDigest.calculate(102L, original));
        assertNotEquals(IamAgentRequestDigest.calculate(101L, original),
                IamAgentRequestDigest.calculate(101L, changed));
    }
    
    @Test
    void roleAssignmentDigestIncludesCompensationExpectedState() {
        AgentRoleAssignmentReq first = AgentRoleAssignmentReq.builder()
                .operationKey("run-a:compensate@1.0.0").roleIds(List.of(7L))
                .expectedCurrentRoleIds(List.of(22L, 21L)).build();
        AgentRoleAssignmentReq reordered = AgentRoleAssignmentReq.builder()
                .operationKey("run-b:compensate@1.0.0").roleIds(List.of(7L))
                .expectedCurrentRoleIds(List.of(21L, 22L, 21L)).build();
        AgentRoleAssignmentReq stale = AgentRoleAssignmentReq.builder()
                .operationKey("run-c:compensate@1.0.0").roleIds(List.of(7L))
                .expectedCurrentRoleIds(List.of(23L)).build();
        
        assertEquals(IamAgentRequestDigest.calculate(101L, first),
                IamAgentRequestDigest.calculate(101L, reordered));
        assertNotEquals(IamAgentRequestDigest.calculate(101L, first),
                IamAgentRequestDigest.calculate(101L, stale));
    }
    
    private AgentUserCreateReq createRequest() {
        return AgentUserCreateReq.builder().operationKey("run-1:create-user@1.0.0")
                .username("agent_zhangsan").nickname("张三").mobile("13800138000")
                .email("zhangsan@example.com").orgId(11L).build();
    }
}
