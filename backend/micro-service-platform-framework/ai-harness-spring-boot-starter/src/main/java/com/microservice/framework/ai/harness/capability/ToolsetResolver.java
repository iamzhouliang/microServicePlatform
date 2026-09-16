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

package com.microservice.framework.ai.harness.capability;

import com.microservice.framework.ai.harness.tool.ToolGovernance;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.tool.GovernedToolProvider;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 汇总 Toolset 贡献并在 Schema 暴露前形成最小能力范围。 */
public final class ToolsetResolver {
    
    private final List<ToolsetContributor> contributors;
    private final List<ToolsetAvailabilityPolicy> policies;
    
    public ToolsetResolver(Collection<ToolsetContributor> contributors,
                           Collection<ToolsetAvailabilityPolicy> policies) {
        this.contributors = List.copyOf(Objects.requireNonNull(contributors, "Toolset 贡献者集合不能为空"));
        this.policies = List.copyOf(Objects.requireNonNull(policies, "Toolset 策略集合不能为空"));
    }
    
    public ResolvedCapabilityScope resolve(ToolsetSelectionContext context) {
        return resolve(context, java.util.Set.of());
    }
    
    public ResolvedCapabilityScope resolve(ToolsetSelectionContext context,
                                           Collection<String> requiredToolIdentities) {
        Objects.requireNonNull(context, "Toolset 选择上下文不能为空");
        java.util.Set<String> requiredIdentities = java.util.Set.copyOf(
                Objects.requireNonNull(requiredToolIdentities, "Tool 身份集合不能为空"));
        List<ToolsetDescriptor> selected = new ArrayList<>();
        Map<String, AiServiceTool> tools = new LinkedHashMap<>();
        Map<String, ToolGovernance> governance = new LinkedHashMap<>();
        List<ToolsetDecision> decisions = new ArrayList<>();
        for (ToolsetContributor contributor : contributors) {
            for (ToolsetContribution contribution : contributor.contribute(context)) {
                ToolsetDescriptor descriptor = contribution.descriptor();
                if (!context.requestedToolsets().isEmpty()
                        && !context.requestedToolsets().contains(descriptor.id())) {
                    decisions.add(ToolsetDecision.hidden(descriptor.id(), "TOOLSET_NOT_REQUESTED"));
                    continue;
                }
                ToolsetDecision decision = evaluate(descriptor, context);
                decisions.add(decision);
                if (!decision.available()) {
                    continue;
                }
                selected.add(descriptor);
                for (AiServiceTool tool : contribution.tools()) {
                    ToolGovernance rule = contribution.governanceByName().get(tool.name());
                    if (!requiredIdentities.isEmpty()
                            && !requiredIdentities.contains(rule.identity())
                            && !requiredIdentities.contains(tool.name() + "@" + rule.version())) {
                        decisions.add(ToolsetDecision.hidden(tool.name(), "TOOL_NOT_REQUESTED"));
                        continue;
                    }
                    if (!DefaultToolsetAvailabilityPolicy.hasPermissionExpression(
                            context.permissions(), rule.requiredPermission())) {
                        decisions.add(ToolsetDecision.hidden(tool.name(), "TOOL_PERMISSION_DENIED"));
                        continue;
                    }
                    if (tools.putIfAbsent(tool.name(), tool) != null) {
                        throw new IllegalArgumentException("已选择的 Toolset 存在 Tool 名称冲突：" + tool.name());
                    }
                    governance.put(tool.name(), rule);
                    decisions.add(ToolsetDecision.available(tool.name()));
                }
            }
        }
        return new ResolvedCapabilityScope(scopeId(context, selected, governance.values()), selected,
                List.copyOf(tools.values()), governance, decisions);
    }
    
    /**
     * 为 Skill、自定义 Agent 或 Workflow 创建按请求解析并绑定治理的官方 ToolProvider。
     *
     * <p>缺少可信选择上下文时返回空集合，不能退回全量工具。</p>
     *
     * @param toolsetIds 允许使用的 Toolset 标识
     * @param toolIdentities 允许使用的精确 Tool 身份
     * @param operationCoordinator 写操作协调器
     * @return 请求级动态 ToolProvider
     */
    public ToolProvider providerForRequirements(Collection<String> toolsetIds,
                                                Collection<String> toolIdentities,
                                                HarnessOperationCoordinator operationCoordinator) {
        java.util.Set<String> requestedToolsets = java.util.Set.copyOf(
                Objects.requireNonNull(toolsetIds, "Toolset 标识集合不能为空"));
        java.util.Set<String> requestedTools = java.util.Set.copyOf(
                Objects.requireNonNull(toolIdentities, "Tool 身份集合不能为空"));
        Objects.requireNonNull(operationCoordinator, "Harness Operation 协调器不能为空");
        return new ToolProvider() {
            
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return provideRequiredTools(request, requestedToolsets, requestedTools, operationCoordinator);
            }
            
            @Override
            public boolean isDynamic() {
                return true;
            }
        };
    }
    
    private ToolProviderResult provideRequiredTools(ToolProviderRequest request,
                                                    java.util.Set<String> requestedToolsets,
                                                    java.util.Set<String> requestedTools,
                                                    HarnessOperationCoordinator operationCoordinator) {
        if (request == null) {
            return ToolProviderResult.builder().build();
        }
        return ToolsetSelectionContext.from(request.invocationContext())
                .map(context -> resolve(context.withRequestedToolsets(requestedToolsets), requestedTools))
                .map(scope -> new GovernedToolProvider(scope.toolProvider(), scope, operationCoordinator)
                        .provideTools(request))
                .orElseGet(() -> ToolProviderResult.builder().build());
    }
    
    private ToolsetDecision evaluate(ToolsetDescriptor descriptor, ToolsetSelectionContext context) {
        for (ToolsetAvailabilityPolicy policy : policies) {
            ToolsetDecision decision = policy.evaluate(descriptor, context);
            if (!decision.available()) {
                return decision;
            }
        }
        return ToolsetDecision.available(descriptor.id());
    }
    
    private static String scopeId(ToolsetSelectionContext context, List<ToolsetDescriptor> selected,
                                  Collection<ToolGovernance> governance) {
        String source = context.tenantId() + "\n" + context.userId() + "\n" + context.conversationId() + "\n"
                + selected.stream().map(descriptor -> descriptor.id() + "@" + descriptor.version()).sorted().toList()
                + "\n" + governance.stream().map(ToolGovernance::identity).sorted().toList();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", impossible);
        }
    }
}
