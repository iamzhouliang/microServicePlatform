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
import com.microservice.framework.ai.harness.tool.ToolGovernanceResolver;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 一次会话或任务解析得到的不可变能力范围。
 *
 * @param scopeId 能力范围摘要
 * @param selectedToolsets 已选择的 Toolset
 * @param tools 最终可见的官方 Tool
 * @param governanceByName 按 Tool 名索引的治理规则
 * @param decisions 低敏选择裁决
 */
public record ResolvedCapabilityScope(String scopeId, List<ToolsetDescriptor> selectedToolsets,
        List<AiServiceTool> tools, Map<String, ToolGovernance> governanceByName,
        List<ToolsetDecision> decisions) implements ToolGovernanceResolver {

    public ResolvedCapabilityScope {
        selectedToolsets = List.copyOf(selectedToolsets);
        tools = List.copyOf(tools);
        governanceByName = Map.copyOf(governanceByName);
        decisions = List.copyOf(decisions);
    }

    ToolProvider toolProvider() {
        return request -> ToolProviderResult.builder().addAll(tools).build();
    }

    /** 按精确审计身份从当前不可变范围解析治理规则。 */
    public Optional<ToolGovernance> resolveIdentity(String identity) {
        return governanceByName.values().stream()
                .filter(rule -> rule.identity().equals(identity))
                .findFirst();
    }

    @Override
    public Optional<ToolGovernance> resolve(ToolSpecification specification) {
        return specification == null ? Optional.empty()
                : Optional.ofNullable(governanceByName.get(specification.name()));
    }
}
