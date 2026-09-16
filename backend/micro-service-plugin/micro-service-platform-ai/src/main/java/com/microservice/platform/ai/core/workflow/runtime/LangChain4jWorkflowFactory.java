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

package com.microservice.platform.ai.core.workflow.runtime;

import com.microservice.platform.ai.core.enums.NodeType;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于已编译的可视化工作流构建 LangChain4j 工作流描述。
 * <p>本地运行时继续负责持久化、暂停恢复和旧节点桥接。本工厂是显式 LangChain4j 边界，
 * 用来保证已编译图与官方顺序、并行、条件、循环工作流模型保持一致。</p>
 *
 * @author xJh
 * @since 2026/05/24
 */
@Component
public class LangChain4jWorkflowFactory {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    public WorkflowModel buildModel(CompiledWorkflow workflow) {
        List<String> builderKinds = new ArrayList<>();
        builderKinds.add("sequence");
        for (CompiledWorkflow.CompiledNode node : workflow.executionOrder()) {
            if (node.type() == NodeType.PARALLEL) {
                builderKinds.add("parallel");
            } else if (node.type() == NodeType.IF_ELSE || node.type() == NodeType.QUESTION_CLASSIFIER) {
                builderKinds.add("conditional");
            } else if (node.type() == NodeType.LOOP || node.type() == NodeType.ITERATION) {
                builderKinds.add("loop");
            }
        }
        return new WorkflowModel(List.copyOf(builderKinds), buildSequenceAgent(workflow));
    }

    UntypedAgent buildSequenceAgent(CompiledWorkflow workflow) {
        Object[] actions = workflow.executionOrder().stream()
                .map(node -> AgenticServices.agentAction(scope -> executeNodeAction(node, scope)))
                .toArray();
        return AgenticServices.sequenceBuilder()
                .name("workflow-" + workflow.startNode().id())
                .subAgents(actions)
                .output(scope -> scope.state())
                .build();
    }

    private void executeNodeAction(CompiledWorkflow.CompiledNode node,
                                   dev.langchain4j.agentic.scope.AgenticScope scope) {
        scope.writeState(node.scopeKey("executed"), true);
        if (node.type() == NodeType.VARIABLE_ASSIGNER) {
            executeVariableAssigner(node, scope);
        } else if (node.type() == NodeType.END) {
            resolveEndOutputs(node, scope).forEach((key, value) -> scope.writeState("outputs." + key, value));
        }
    }

    private void executeVariableAssigner(CompiledWorkflow.CompiledNode node,
                                         dev.langchain4j.agentic.scope.AgenticScope scope) {
        Object assignments = node.config().get("assignments");
        if (!(assignments instanceof Collection<?> collection)) {
            return;
        }
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Object variableName = map.get("variableName");
            if (variableName == null) {
                continue;
            }
            Object rawValue = map.get("value");
            Object value = rawValue instanceof String text ? resolveTemplate(text, scope) : rawValue;
            scope.writeState(node.scopeKey(String.valueOf(variableName)), value);
        }
    }

    private Map<String, Object> resolveEndOutputs(CompiledWorkflow.CompiledNode node,
                                                  dev.langchain4j.agentic.scope.AgenticScope scope) {
        Object configuredOutputs = node.config().get("outputs");
        if (!(configuredOutputs instanceof Collection<?> collection)) {
            return Map.of();
        }
        Map<String, Object> outputs = new LinkedHashMap<>();
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> output)) {
                continue;
            }
            Object name = output.get("name");
            if (name == null) {
                continue;
            }
            Object rawValue = output.get("value");
            Object value = rawValue instanceof String text ? resolveTemplate(text, scope) : rawValue;
            outputs.put(String.valueOf(name), value);
            scope.writeState(node.scopeKey(String.valueOf(name)), value);
        }
        return outputs;
    }

    private Object resolveTemplate(String template, dev.langchain4j.agentic.scope.AgenticScope scope) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        boolean wholeValueReference = template.trim().matches("^\\{\\{[^}]+}}$");
        Object wholeValue = null;
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = scope.readState(key);
            if (wholeValueReference) {
                wholeValue = value;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(Objects.toString(value, "")));
        }
        matcher.appendTail(result);
        return wholeValueReference ? wholeValue : result.toString();
    }

    public record WorkflowModel(List<String> builderKinds, UntypedAgent sequenceAgent) {

        public boolean uses(String builderKind) {
            return builderKinds.contains(builderKind);
        }
    }
}
