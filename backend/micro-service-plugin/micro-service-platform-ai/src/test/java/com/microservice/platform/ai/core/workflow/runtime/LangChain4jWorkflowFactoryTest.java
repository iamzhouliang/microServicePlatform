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
import com.microservice.platform.ai.domain.entity.workflow.WorkflowEdge;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LangChain4jWorkflowFactoryTest {

    @Test
    void buildsOfficialLangChain4jWorkflowModelFromCompiledGraph() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("parallel", NodeType.PARALLEL, "并行"),
                        node("left", NodeType.IF_ELSE, "条件", Map.of("branches", List.of(Map.of("id", "ok", "type", "ELSE")))),
                        node("right", NodeType.LOOP, "循环", Map.of("maxIterations", 2)),
                        node("end", NodeType.END, "结束")),
                List.of(edge("start", "parallel"), edge("parallel", "left"), edge("parallel", "right"),
                        edge("left", "branch:ok", "end"), edge("right", "branch:exit", "end")));

        LangChain4jWorkflowFactory.WorkflowModel model = new LangChain4jWorkflowFactory()
                .buildModel(new WorkflowCompiler().compile(graph));

        assertThat(model.sequenceAgent()).isNotNull();
        assertThat(model.uses("sequence")).isTrue();
        assertThat(model.uses("parallel")).isTrue();
        assertThat(model.uses("conditional")).isTrue();
        assertThat(model.uses("loop")).isTrue();
    }

    @Test
    void sequenceAgentExecutesCompiledNodeActions() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("assign", NodeType.VARIABLE_ASSIGNER, "写入", Map.of("assignments", List.of(
                                Map.of("variableName", "answer", "value", "hello")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.assign.answer}}"))))),
                List.of(edge("start", "assign"), edge("assign", "end")));

        LangChain4jWorkflowFactory.WorkflowModel model = new LangChain4jWorkflowFactory()
                .buildModel(new WorkflowCompiler().compile(graph));

        Object output = model.sequenceAgent().invoke(Map.of());

        assertThat(output)
                .isInstanceOf(Map.class)
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("nodes.start.executed", true)
                .containsEntry("nodes.assign.executed", true)
                .containsEntry("nodes.end.executed", true)
                .containsEntry("nodes.assign.answer", "hello")
                .containsEntry("outputs.answer", "hello");
    }

    private static WorkflowGraph graph(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        return WorkflowGraph.builder().nodes(nodes).edges(edges).build();
    }

    private static WorkflowNode node(String id, NodeType type, String label) {
        return node(id, type, label, Map.of());
    }

    private static WorkflowNode node(String id, NodeType type, String label, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(label)
                .position(WorkflowNode.Position.builder().x(0.0).y(0.0).build())
                .data(new HashMap<>(data))
                .build();
    }

    private static WorkflowEdge edge(String source, String target) {
        return edge(source, null, target);
    }

    private static WorkflowEdge edge(String source, String sourceHandle, String target) {
        return WorkflowEdge.builder()
                .id(source + "-" + target)
                .source(source)
                .sourceHandle(sourceHandle)
                .target(target)
                .targetHandle("input")
                .build();
    }
}
