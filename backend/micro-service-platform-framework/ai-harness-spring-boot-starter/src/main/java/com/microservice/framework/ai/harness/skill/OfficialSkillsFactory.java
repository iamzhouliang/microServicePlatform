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

package com.microservice.framework.ai.harness.skill;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.Skills;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.List;

/**
 * 创建 LangChain4j 官方 Skills，并在激活成功后追加精确修订审计。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class OfficialSkillsFactory {
    
    private static final String ACTIVATE_SKILL = "activate_skill";
    private static final String ACTIVATED_SKILL_ATTRIBUTE = "activated_skill";
    
    private final PublishedSkillResolver resolver;
    private final SkillActivationLedger activationLedger;
    private final Clock clock;
    
    public OfficialSkillsFactory(PublishedSkillResolver resolver, SkillActivationLedger activationLedger) {
        this(resolver, activationLedger, Clock.systemUTC());
    }
    
    public OfficialSkillsFactory(PublishedSkillResolver resolver, SkillActivationLedger activationLedger,
                                 Clock clock) {
        this.resolver = Objects.requireNonNull(resolver, "已发布 Skill 解析器不能为空");
        this.activationLedger = Objects.requireNonNull(activationLedger, "Skill 激活台账不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
    }
    
    public OfficialSkillsRuntime create() {
        return create(null);
    }
    
    /**
     * 在请求线程预加载会话已固定的精确修订，避免异步 Tool Loop 访问业务存储。
     *
     * @param invocation 当前可信调用身份；为空时只加载当前发布修订
     * @return 使用 LangChain4j 官方 Skills 构建的本轮运行时
     */
    public OfficialSkillsRuntime create(HarnessInvocation invocation) {
        Map<String, PublishedSkill> revisions = revisionsFor(invocation, activeRevisions());
        if (revisions.isEmpty()) {
            return OfficialSkillsRuntime.empty();
        }
        Skills skills = Skills.from(revisions.values().stream().map(PublishedSkill::toOfficialSkill).toList());
        return OfficialSkillsRuntime.from(skills, auditActivation(skills.toolProvider(), revisions));
    }
    
    private Map<String, PublishedSkill> revisionsFor(HarnessInvocation invocation,
                                                     Map<String, PublishedSkill> active) {
        if (invocation == null) {
            return active;
        }
        Map<String, PublishedSkill> revisions = new LinkedHashMap<>(active);
        List<SkillActivation> activations = activationLedger.findConversation(
                invocation.tenantId(), invocation.userId(), invocation.conversationId());
        Map<String, SkillActivation> latestByCode = new LinkedHashMap<>();
        activations.forEach(activation -> latestByCode.putIfAbsent(activation.skillCode(), activation));
        latestByCode.forEach((code, activation) -> revisions.put(code, resolver.resolveExact(
                activation.skillCode(), activation.skillVersion(), activation.contentDigest())
                .orElseThrow(() -> new IllegalStateException("已激活 Skill 精确修订不可用："
                        + activation.skillCode() + "@" + activation.skillVersion()))));
        return Map.copyOf(revisions);
    }
    
    private Map<String, PublishedSkill> activeRevisions() {
        Map<String, PublishedSkill> revisions = new LinkedHashMap<>();
        resolver.resolveActive().forEach(revision -> {
            PublishedSkill previous = revisions.putIfAbsent(revision.code(), revision);
            if (previous != null) {
                throw new IllegalStateException("同一技能代码存在多个激活修订：" + revision.code());
            }
        });
        return Map.copyOf(revisions);
    }
    
    private ToolProvider auditActivation(ToolProvider official, Map<String, PublishedSkill> revisions) {
        return new ToolProvider() {
            
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                ToolProviderResult original = official.provideTools(request);
                ToolProviderResult.Builder result = ToolProviderResult.builder();
                original.aiServiceTools().forEach(tool -> result.add(ACTIVATE_SKILL.equals(tool.name())
                        ? decorateActivation(tool, revisions)
                        : tool));
                return result.build();
            }
            
            @Override
            public boolean isDynamic() {
                return official.isDynamic();
            }
        };
    }
    
    private AiServiceTool decorateActivation(AiServiceTool tool, Map<String, PublishedSkill> revisions) {
        ToolExecutor officialExecutor = tool.toolExecutor();
        ToolExecutor auditedExecutor = new ToolExecutor() {
            
            @Override
            public String execute(dev.langchain4j.agent.tool.ToolExecutionRequest request, Object memoryId) {
                return officialExecutor.execute(request, memoryId);
            }
            
            @Override
            public ToolExecutionResult executeWithContext(
                                                          dev.langchain4j.agent.tool.ToolExecutionRequest request,
                                                          dev.langchain4j.invocation.InvocationContext context) {
                ToolExecutionResult execution = officialExecutor.executeWithContext(request, context);
                Object activated = execution.attributes().get(ACTIVATED_SKILL_ATTRIBUTE);
                if (activated instanceof String code) {
                    recordActivation(code, revisions, context);
                }
                return execution;
            }
        };
        return tool.toBuilder().toolExecutor(auditedExecutor).build();
    }
    
    private void recordActivation(String code, Map<String, PublishedSkill> revisions,
                                  dev.langchain4j.invocation.InvocationContext context) {
        PublishedSkill revision = revisions.get(code);
        if (revision == null) {
            throw new IllegalStateException("官方 Skills 激活了未发布的技能：" + code);
        }
        HarnessInvocation invocation = HarnessInvocation.from(context)
                .orElseThrow(() -> new IllegalArgumentException("Skill 激活缺少可信调用身份"));
        activationLedger.record(new SkillActivation(invocation.tenantId(), invocation.userId(),
                invocation.conversationId(), invocation.turnId(), revision.code(), revision.version(),
                revision.digest(), clock.instant()));
    }
}
