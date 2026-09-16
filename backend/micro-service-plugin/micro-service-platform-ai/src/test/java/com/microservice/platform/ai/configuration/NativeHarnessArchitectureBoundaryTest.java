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

package com.microservice.platform.ai.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microservice.platform.ai.repository.AiSkillActivationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Repository;

/**
 * 原生 Harness 架构边界契约。
 * <p>聊天入口只能依赖 LangChain4j 原生 Tool Loop，旧计划、运行步骤和聊天门面不得重新进入生产类路径。</p>
 *
 * @author xJh
 * @since 2026-07
 */
class NativeHarnessArchitectureBoundaryTest {

    private static final String[] REMOVED_TYPES = {
            "com.microservice.platform.ai.controller.AgentChatController",
            "com.microservice.platform.ai.service.agent.AgentChatFacade",
            "com.microservice.platform.ai.service.agent.PlatformAssistantToolService",
            "com.microservice.platform.ai.core.agent.planner.IamCommandPlanner",
            "com.microservice.framework.ai.harness.planner.AgentPlanner",
            "com.microservice.framework.ai.harness.planner.LangChain4jAgentPlanner",
            "com.microservice.framework.ai.harness.model.ActionPlan",
            "com.microservice.framework.ai.harness.model.ActionStep",
            "com.microservice.framework.ai.harness.tool.HarnessToolCatalog",
            "com.microservice.framework.ai.harness.skill.LangChain4jSkillToolProvider",
            "com.microservice.framework.ai.harness.capability.AgentCapabilityProvider",
            "com.microservice.framework.ai.harness.capability.CapabilityToolIndex",
            "com.microservice.platform.ai.core.provider.mcp.ContextAwareMcpToolProvider",
            "com.microservice.platform.ai.core.provider.mcp.McpToolProviderFactory",
            "com.microservice.platform.ai.core.provider.mcp.McpToolProviderContext"
    };

    @Test
    void productionClasspathDoesNotContainLegacyPlannerOrChatFacade() {
        for (String type : REMOVED_TYPES) {
            assertThatThrownBy(() -> Class.forName(type))
                    .as("旧架构类型必须从生产类路径删除：%s", type)
                    .isInstanceOf(ClassNotFoundException.class);
        }
    }

    @Test
    void skillActivationMapperFollowsApplicationScanContract() {
        assertThat(AiSkillActivationMapper.class).hasAnnotation(Repository.class);
    }
}
