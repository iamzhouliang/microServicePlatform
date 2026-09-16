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

package com.microservice.platform.ai.config;

import cn.dev33.satoken.stp.StpUtil;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.ApprovalSummaryProjector;
import com.microservice.framework.ai.harness.runtime.model.ApprovalSummary;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorizationProvider;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.runtime.HarnessVerifierResolver;
import com.microservice.framework.ai.harness.skill.OfficialSkillsFactory;
import com.microservice.framework.ai.harness.skill.PublishedSkillResolver;
import com.microservice.framework.ai.harness.skill.SkillActivationLedger;
import com.microservice.platform.ai.core.assistant.service.PlatformAssistantRuntime;
import com.microservice.platform.ai.core.assistant.service.PlatformPromptAssembler;
import com.microservice.platform.ai.core.agent.tool.iam.IamAgentTools;
import com.microservice.platform.ai.core.agent.tool.iam.IamProvisionResultVerifier;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 平台助手原生 Tool Loop 组合配置。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Configuration(proxyBeanMethods = false)
public class AgentRuntimeConfiguration {

    /**
     * 提供当前请求的可信委托授权。
     *
     * @return 从认证上下文读取令牌的授权提供器
     */
    @Bean
    public DelegatedAuthorizationProvider delegatedAuthorizationProvider() {
        return () -> new DelegatedAuthorization(StpUtil.getTokenValue());
    }

    /**
     * 创建 IAM 写操作的安全审批投影器，只展示必要业务字段。
     *
     * @param objectMapper JSON 参数解析器
     * @return 不持久化联系方式和原始参数的审批投影器
     */
    @Bean
    public ApprovalSummaryProjector approvalSummaryProjector(ObjectMapper objectMapper) {
        return (governance, request) -> {
            if (!IamAgentTools.PROVISION_USER.equals(governance.toolName())) {
                return ApprovalSummaryProjector.generic().project(governance, request);
            }
            try {
                var arguments = objectMapper.readTree(request.arguments());
                String username = arguments.path("username").asText("未命名账号");
                String orgId = arguments.path("orgId").asText("未指定组织");
                String roleIds = arguments.path("roleIds").isArray()
                        ? objectMapper.writeValueAsString(arguments.path("roleIds"))
                        : "[]";
                return new ApprovalSummary("用户账号：" + username,
                        "在组织 " + orgId + " 创建用户并分配角色 " + roleIds,
                        "高风险：创建账号并授予业务角色");
            } catch (Exception failure) {
                throw new IllegalArgumentException("无法生成安全的 IAM 审批摘要", failure);
            }
        };
    }

    @Bean
    public HarnessVerifierResolver harnessVerifierResolver(IamAgentFeign feign,
                                                           DelegatedAuthorizationProvider authorizationProvider, ObjectMapper objectMapper) {
        IamProvisionResultVerifier verifier = new IamProvisionResultVerifier(
                feign, authorizationProvider, objectMapper);
        return governance -> IamAgentTools.PROVISION_USER.equals(governance.toolName())
                ? java.util.Optional.of(verifier)
                : java.util.Optional.empty();
    }

    @Bean
    public OfficialSkillsFactory officialSkillsFactory(PublishedSkillResolver resolver,
                                                       SkillActivationLedger activationLedger) {
        return new OfficialSkillsFactory(resolver, activationLedger);
    }

    @Bean
    public PlatformAssistantRuntime platformAssistantRuntime(OfficialSkillsFactory skillsFactory,
                                                             ToolsetResolver toolsetResolver, HarnessOperationCoordinator coordinator,
                                                             HarnessOperationStore operationStore, PendingActionService pendingActionService,
                                                             PlatformPromptAssembler promptAssembler) {
        return new PlatformAssistantRuntime(skillsFactory, toolsetResolver, coordinator, operationStore,
                pendingActionService, promptAssembler);
    }
}
