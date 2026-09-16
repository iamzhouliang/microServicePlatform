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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("智能体工作流编译器")
class WorkflowCompilerTest {

    private final WorkflowCompiler compiler = new WorkflowCompiler();

    @Test
    @DisplayName("编译线性工作流时使用稳定节点 ID 生成 scope key，节点标签只做展示")
    void compileLinearWorkflowUsesStableScopeKeys() {
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始节点"), node("llm", NodeType.LLM, "可改名LLM"), node("end", NodeType.END, "结束")),
                List.of(edge("start", "llm"), edge("llm", "end")));

        CompiledWorkflow compiled = compiler.compile(graph);

        assertThat(compiled.startNode().id()).isEqualTo("start");
        assertThat(compiled.terminalNodes()).extracting(CompiledWorkflow.CompiledNode::id).containsExactly("end");
        assertThat(compiled.node("llm").displayName()).isEqualTo("可改名LLM");
        assertThat(compiled.node("llm").scopeKey("result")).isEqualTo("nodes.llm.result");
        assertThat(compiled.executionOrder()).extracting(CompiledWorkflow.CompiledNode::id)
                .containsExactly("start", "llm", "end");
    }

    @Test
    @DisplayName("没有 START 节点时拒绝编译")
    void compileRejectsGraphWithoutStart() {
        WorkflowGraph graph = graph(List.of(node("end", NodeType.END, "结束")), List.of());

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("START");
    }

    @Test
    @DisplayName("没有终止节点时拒绝编译")
    void compileRejectsGraphWithoutTerminalNode() {
        WorkflowGraph graph = graph(List.of(node("start", NodeType.START, "开始"), node("llm", NodeType.LLM, "LLM")), List.of(edge("start", "llm")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("终止 END 节点");
    }

    @Test
    @DisplayName("条件分支必须引用已声明的分支 ID")
    void compileRejectsUnknownConditionalBranchHandle() {
        Map<String, Object> data = new HashMap<>();
        data.put("branches", List.of(Map.of("id", "approved", "label", "通过", "type", "IF")));
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始"), node("condition", NodeType.IF_ELSE, "判断", data), node("end", NodeType.END, "结束")),
                List.of(edge("start", "condition"), edge("condition", "rejected", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejected");
    }

    @Test
    @DisplayName("问题分类器分支必须引用 categories 中已声明的类别 ID")
    void compileRejectsUnknownQuestionClassifierCategoryHandle() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("classifier", NodeType.QUESTION_CLASSIFIER, "分类", Map.of("categories", List.of(
                                Map.of("id", "billing", "name", "账单")))),
                        node("end", NodeType.END, "结束")),
                List.of(edge("start", "classifier"), edge("classifier", "tech", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tech");
    }

    @Test
    @DisplayName("循环节点必须设置正数 maxIterations")
    void compileRejectsLoopWithoutPositiveMaxIterations() {
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始"), node("loop", NodeType.LOOP, "循环", Map.of("maxIterations", 0)), node("end", NodeType.END, "结束")),
                List.of(edge("start", "loop"), edge("loop", "branch:exit", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxIterations");
    }

    @Test
    @DisplayName("拒绝不可达节点，保存图必须能编译为完整 Workflow DSL")
    void compileRejectsUnreachableNodes() {
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始"), node("end", NodeType.END, "结束"),
                        node("orphan", NodeType.LLM, "孤儿节点")),
                List.of(edge("start", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不可达节点");
    }

    @Test
    @DisplayName("并行路由至少需要两个分支")
    void compileRejectsParallelWithSingleBranch() {
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始"), node("parallel", NodeType.PARALLEL, "并行"),
                        node("end", NodeType.END, "结束")),
                List.of(edge("start", "parallel"), edge("parallel", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PARALLEL");
    }

    @Test
    @DisplayName("普通节点不能形成环，循环必须使用 LOOP 编排节点表达")
    void compileRejectsUnexpectedCycle() {
        WorkflowGraph graph = graph(
                List.of(node("start", NodeType.START, "开始"), node("a", NodeType.VARIABLE_ASSIGNER, "A"),
                        node("end", NodeType.END, "结束")),
                List.of(edge("start", "a"), edge("a", "a"), edge("a", "end")));

        assertThatThrownBy(() -> compiler.compile(graph))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("环路");
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
