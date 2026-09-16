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
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolService;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Toolset 描述、官方 Tool 与治理元数据的不可变贡献结果。
 *
 * @param descriptor Toolset 描述
 * @param tools LangChain4j 官方 Tool
 * @param governanceByName 按 Tool 名索引的治理规则
 */
public record ToolsetContribution(ToolsetDescriptor descriptor, List<AiServiceTool> tools,
        Map<String, ToolGovernance> governanceByName) {

    private static final Pattern MODEL_TOOL_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_-]+$");

    public ToolsetContribution {
        descriptor = Objects.requireNonNull(descriptor, "Toolset 描述不能为空");
        tools = List.copyOf(Objects.requireNonNull(tools, "Tool 集合不能为空"));
        governanceByName = Map.copyOf(Objects.requireNonNull(governanceByName, "治理集合不能为空"));
    }

    public static ToolsetContribution fromBeans(ToolsetDescriptor descriptor, Collection<Object> beans,
                                                Collection<ToolGovernance> governances) {
        Objects.requireNonNull(beans, "Tool Bean 集合不能为空");
        Map<String, ToolGovernance> governance = new LinkedHashMap<>();
        Objects.requireNonNull(governances, "治理集合不能为空").forEach(rule -> {
            if (governance.putIfAbsent(rule.toolName(), rule) != null) {
                throw new IllegalArgumentException("Tool 治理名称重复：" + rule.toolName());
            }
        });
        Map<String, AiServiceTool> tools = new LinkedHashMap<>();
        beans.forEach(bean -> ToolService.findTools(bean).forEach(tool -> {
            validateToolName(tool.name());
            if (!governance.containsKey(tool.name())) {
                throw new IllegalArgumentException("业务 Tool 缺少显式治理元数据：" + tool.name());
            }
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalArgumentException("业务 Tool 名称重复：" + tool.name());
            }
        }));
        governance.keySet().stream().filter(name -> !tools.containsKey(name)).findFirst()
                .ifPresent(name -> {
                    throw new IllegalArgumentException("Tool 治理元数据没有对应的业务 Tool：" + name);
                });
        governance.values().stream().filter(rule -> !descriptor.id().equals(rule.toolsetId())).findFirst()
                .ifPresent(rule -> {
                    throw new IllegalArgumentException("Tool 治理所属 Toolset 与贡献描述不一致：" + rule.toolName());
                });
        return new ToolsetContribution(descriptor, List.copyOf(tools.values()), governance);
    }

    private static void validateToolName(String name) {
        if (name == null || !MODEL_TOOL_NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("Tool 名称只能包含字母、数字、下划线或中划线：" + name);
        }
    }
}
