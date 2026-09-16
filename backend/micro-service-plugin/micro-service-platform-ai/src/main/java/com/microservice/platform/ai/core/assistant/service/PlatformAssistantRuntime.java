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

import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.prompt.PromptLayers;
import com.microservice.framework.ai.harness.skill.OfficialSkillsFactory;
import com.microservice.framework.ai.harness.skill.OfficialSkillsRuntime;
import com.microservice.framework.ai.harness.capability.ResolvedCapabilityScope;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.tool.GovernedToolProvider;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.PendingActionToolProvider;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import com.microservice.framework.ai.harness.tool.ToolGovernanceResolver;
import dev.langchain4j.service.tool.ToolProvider;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 为每次平台助手会话创建官方 Skills 与受治理 ToolProvider 组合。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class PlatformAssistantRuntime {

    private final OfficialSkillsFactory skillsFactory;
    private final ToolsetResolver toolsetResolver;
    private final HarnessOperationCoordinator operationCoordinator;
    private final HarnessOperationStore operationStore;
    private final PendingActionService pendingActionService;
    private final PlatformPromptAssembler promptAssembler;

    public PlatformAssistantRuntime(OfficialSkillsFactory skillsFactory, ToolsetResolver toolsetResolver,
                                    HarnessOperationCoordinator operationCoordinator, HarnessOperationStore operationStore,
                                    PendingActionService pendingActionService, PlatformPromptAssembler promptAssembler) {
        this.skillsFactory = skillsFactory;
        this.toolsetResolver = toolsetResolver;
        this.operationCoordinator = operationCoordinator;
        this.operationStore = operationStore;
        this.pendingActionService = pendingActionService;
        this.promptAssembler = promptAssembler;
    }

    public Setup create(com.microservice.framework.ai.harness.runtime.HarnessInvocation invocation) {
        OfficialSkillsRuntime skills = skillsFactory.create(invocation);
        ToolGovernanceResolver resolver = specification -> systemGovernance(specification.name());
        ToolProvider governedSkills = new GovernedToolProvider(skills.toolProvider(), resolver,
                operationCoordinator);
        ToolProvider pendingActions = new PendingActionToolProvider(operationStore, pendingActionService);
        String capabilityIndex = skills.formatAvailableSkills();
        PromptLayers promptLayers = promptAssembler.assemble(new PlatformPromptContext(
                PlatformPromptProfile.BUSINESS, false, capabilityIndex, ""));
        return new Setup(List.of(governedSkills, pendingActions), promptLayers, capabilityIndex);
    }

    /**
     * 返回语义路由使用的只读能力目录。
     * <p>目录来自应用启动时已经建立的治理索引，不读取 Skill 存储，也不创建 ToolProvider 或 Harness
     * 运行时，确保普通聊天不会被业务运行时初始化失败阻断。</p>
     *
     * @param context 服务端构造的 Toolset 选择上下文
     * @return 按业务域稳定排序的紧凑能力目录
     */
    public String routingCapabilityIndex(ToolsetSelectionContext context) {
        ResolvedCapabilityScope scope = toolsetResolver.resolve(context);
        return scope.governanceByName().values().stream()
                .sorted(Comparator.comparing(ToolGovernance::domain).thenComparing(ToolGovernance::toolName))
                .map(rule -> "%s | %s | %s".formatted(
                        rule.domain(), rule.toolName(), rule.write() ? "写入" : "只读"))
                .collect(Collectors.joining("\n"));
    }

    private static Optional<ToolGovernance> systemGovernance(String name) {
        if (!"activate_skill".equals(name) && !"read_skill_resource".equals(name)) {
            return Optional.empty();
        }
        return Optional.of(new ToolGovernance(name, "system:skills", "skills", "1.17.2", "*", HarnessRiskLevel.LOW,
                false, true, false, false, null, null));
    }

    public record Setup(List<ToolProvider> toolProviders, PromptLayers promptLayers, String capabilityIndex) {
    }
}
