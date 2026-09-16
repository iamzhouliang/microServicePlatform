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

package com.microservice.platform.ai.core.assistant.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.microservice.framework.ai.harness.capability.DefaultToolsetAvailabilityPolicy;
import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetDescriptor;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.skill.OfficialSkillsFactory;
import com.microservice.framework.ai.harness.skill.OfficialSkillsRuntime;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PlatformAssistantRuntimePromptTest {

    @Test
    @DisplayName("路由能力索引只读取 Toolset 范围，不初始化官方 Skills")
    void routingCapabilityIndexDoesNotInitializeOfficialSkills() {
        OfficialSkillsFactory skillsFactory = mock(OfficialSkillsFactory.class);
        RoutingTools bean = new RoutingTools();
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of("BUSINESS"), Set.of(), Set.of(), Set.of(), Set.of(),
                false);
        ToolsetContribution contribution = ToolsetContribution.fromBeans(descriptor, List.of(bean), List.of(
                governance("iam_search_user", false), governance("iam_provision_user", true)));
        ToolsetResolver resolver = new ToolsetResolver(List.of(context -> List.of(contribution)),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        PlatformAssistantRuntime runtime = runtime(skillsFactory, resolver);

        String index = runtime.routingCapabilityIndex(selection());

        assertThat(index).contains("iam", "iam_search_user", "iam_provision_user", "只读", "写入");
        verifyNoInteractions(skillsFactory);
    }

    @Test
    void shouldExposeCompactSkillIndexInSessionPromptLayer() {
        OfficialSkillsFactory skillsFactory = mock(OfficialSkillsFactory.class);
        ToolProvider emptyProvider = request -> ToolProviderResult.builder().build();
        HarnessInvocation invocation = invocation();
        when(skillsFactory.create(invocation)).thenReturn(
                new OfficialSkillsRuntime(emptyProvider, "iam-user-provision：创建用户并分配角色"));
        PlatformAssistantRuntime runtime = runtime(skillsFactory,
                new ToolsetResolver(List.of(), List.of(new DefaultToolsetAvailabilityPolicy())));

        PlatformAssistantRuntime.Setup setup = runtime.create(invocation);

        assertThat(setup.capabilityIndex()).isEqualTo("iam-user-provision：创建用户并分配角色");
        assertThat(setup.promptLayers().sessionContext()).contains("iam-user-provision：创建用户并分配角色");
    }

    @Test
    void businessStablePromptShouldNotPromiseAutomaticExecutionOrExposeHarness() {
        String prompt = new PlatformPromptAssembler().assemble(new PlatformPromptContext(
                PlatformPromptProfile.BUSINESS, false, "可用技能", "")).render();

        assertThat(prompt).contains("审批通过不会自动执行", "请用户在获知审批通过后再发送一条继续消息",
                "不得向用户提及技能激活、Skill 或 Tool 名称",
                "本轮调用对应的权威读取工具", "不得使用历史工具结果代替本轮查询");
    }

    @Test
    void chatWebSearchPromptShouldKeepSourceAndCitationConstraints() {
        String prompt = new PlatformPromptAssembler().assemble(new PlatformPromptContext(
                PlatformPromptProfile.CHAT, true, "", "")).render();

        assertThat(prompt).contains("不得声称无法访问互联网", "官方网站、官方文档、监管机构",
                "用户指定来源、网站或域名", "site:目标官方域名", "事实与推测分开", "[数字] 角标");
    }

    private static PlatformAssistantRuntime runtime(OfficialSkillsFactory skillsFactory, ToolsetResolver resolver) {
        return new PlatformAssistantRuntime(skillsFactory, resolver, mock(HarnessOperationCoordinator.class),
                mock(HarnessOperationStore.class), mock(PendingActionService.class), new PlatformPromptAssembler());
    }

    private static HarnessInvocation invocation() {
        return new HarnessInvocation("2", "1", "100", "operation", "turn", Set.of(), false);
    }

    private static ToolsetSelectionContext selection() {
        return new ToolsetSelectionContext("2", "1", "100", "turn", "BUSINESS", "PLATFORM_ASSISTANT",
                "test", Set.of(), Set.of(), Set.of());
    }

    private static ToolGovernance governance(String name, boolean write) {
        return new ToolGovernance(name, "module:iam:user-management", "iam", "1.0.0", "*",
                write ? HarnessRiskLevel.HIGH : HarnessRiskLevel.LOW,
                write, true, write, write, write ? "verify" : null, null);
    }

    private static final class RoutingTools {

        @Tool(name = "iam_search_user", value = "查询用户")
        public String search() {
            return "用户";
        }

        @Tool(name = "iam_provision_user", value = "创建用户")
        public String provision() {
            return "已创建";
        }
    }
}
