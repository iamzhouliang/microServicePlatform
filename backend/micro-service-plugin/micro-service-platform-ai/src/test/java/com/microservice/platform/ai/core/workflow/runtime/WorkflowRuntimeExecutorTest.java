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
import com.microservice.platform.ai.core.workflow.runtime.adapter.WorkflowNodeAdapter;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowEdge;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("工作流运行时执行器")
class WorkflowRuntimeExecutorTest {

    private final WorkflowCompiler compiler = new WorkflowCompiler();
    private final WorkflowRuntimeExecutor executor = new WorkflowRuntimeExecutor();

    @Test
    @DisplayName("Spring 创建运行时执行器时必须注入工作流节点适配器")
    void springConstructorInjectsWorkflowNodeAdapter() throws NoSuchMethodException {
        Constructor<WorkflowRuntimeExecutor> constructor =
                WorkflowRuntimeExecutor.class.getConstructor(ObjectProvider.class);

        assertThat(constructor.isAnnotationPresent(Autowired.class)).isTrue();
    }

    @Test
    @DisplayName("执行 START -> VARIABLE_ASSIGNER -> END 时使用稳定 scope key 输出")
    void executeVariableWorkflowWritesStableScopeKeys() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "旧开始"),
                        node("assign", NodeType.VARIABLE_ASSIGNER, "可随便改名", Map.of("assignments", List.of(
                                Map.of("variableName", "greeting", "type", "EXPRESSION", "value", "hello {{inputs.question}}", "variableType", "string")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.assign.greeting}}"))))),
                List.of(edge("start", "assign"), edge("assign", "end")));
        CompiledWorkflow compiled = compiler.compile(graph);

        WorkflowExecutionResult result = executor.execute(compiled, Map.of("question", "hi"));

        assertThat(result.success()).isTrue();
        assertThat(result.scope().read("inputs.question")).isEqualTo("hi");
        assertThat(result.scope().read("nodes.assign.greeting")).isEqualTo("hello hi");
        assertThat(result.outputs()).containsEntry("answer", "hello hi");
        assertThat(result.executedNodeIds()).containsExactly("start", "assign", "end");
    }

    @Test
    @DisplayName("VARIABLE_ASSIGNER 按当前强类型契约解析变量引用并转换类型")
    void executeVariableAssignerUsesCurrentConfigContract() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("assign", NodeType.VARIABLE_ASSIGNER, "赋值", Map.of("assignments", List.of(
                                Map.of("variableName", "copied", "type", "VARIABLE", "value", "{{inputs.count}}", "variableType", "number"),
                                Map.of("variableName", "message", "type", "EXPRESSION", "value", "count={{inputs.count}}", "variableType", "string")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "count", "value", "{{nodes.assign.copied}}"),
                                Map.of("name", "message", "value", "{{nodes.assign.message}}"))))),
                List.of(edge("start", "assign"), edge("assign", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of("count", "42"));

        assertThat(result.success()).isTrue();
        assertThat(result.scope().read("nodes.assign.copied")).isEqualTo(42L);
        assertThat(result.outputs()).containsEntry("count", 42L);
        assertThat(result.outputs()).containsEntry("message", "count=42");
    }

    @Test
    @DisplayName("IF_ELSE 分支按 sourceHandle 执行，不依赖节点 label")
    void executeConditionalWorkflowRoutesByBranchHandle() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("condition", NodeType.IF_ELSE, "判断", Map.of("branches", List.of(
                                Map.of("id", "yes", "label", "Yes", "type", "IF", "conditions", List.of(
                                        Map.of("variable", "inputs.approved", "operator", "EQUALS", "value", true))),
                                Map.of("id", "no", "label", "No", "type", "ELSE")))),
                        node("yesNode", NodeType.VARIABLE_ASSIGNER, "分支A", Map.of("assignments", List.of(
                                Map.of("variableName", "result", "type", "LITERAL", "value", "approved")))),
                        node("noNode", NodeType.VARIABLE_ASSIGNER, "分支B", Map.of("assignments", List.of(
                                Map.of("variableName", "result", "type", "LITERAL", "value", "rejected")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(Map.of("name", "answer", "value", "{{nodes.yesNode.result}}"))))),
                List.of(edge("start", "condition"), edge("condition", "branch:yes", "yesNode"), edge("condition", "branch:no", "noNode"), edge("yesNode", "end"), edge("noNode", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of("approved", true));

        assertThat(result.success()).isTrue();
        assertThat(result.executedNodeIds()).containsExactly("start", "condition", "yesNode", "end");
        assertThat(result.scope().read("nodes.yesNode.result")).isEqualTo("approved");
        assertThat(result.scope().has("nodes.noNode.result")).isFalse();
    }

    @Test
    @DisplayName("PARALLEL 路由会执行所有分支并在后续聚合节点读取稳定 scope key")
    void executeParallelBranchesAndAggregateOutputs() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("parallel", NodeType.PARALLEL, "并行"),
                        node("left", NodeType.VARIABLE_ASSIGNER, "左", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "L")))),
                        node("right", NodeType.VARIABLE_ASSIGNER, "右", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "R")))),
                        node("aggregate", NodeType.VARIABLE_AGGREGATOR, "聚合", Map.of("groups", List.of(
                                Map.of("outputVariable", "joined", "sourceVariables", List.of("{{nodes.left.value}}", "{{nodes.right.value}}"), "strategy", "MERGE_TO_ARRAY")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.aggregate.joined}}"))))),
                List.of(edge("start", "parallel"), edge("parallel", "left"), edge("parallel", "right"),
                        edge("left", "aggregate"), edge("right", "aggregate"), edge("aggregate", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.scope().read("nodes.aggregate.joined")).isEqualTo(List.of("L", "R"));
        assertThat(result.outputs()).containsEntry("answer", List.of("L", "R"));
    }

    @Test
    @DisplayName("VARIABLE_AGGREGATOR 只读取当前 outputVariable/sourceVariables 契约")
    void executeVariableAggregatorUsesCurrentConfigContract() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("left", NodeType.VARIABLE_ASSIGNER, "左", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "left")))),
                        node("right", NodeType.VARIABLE_ASSIGNER, "右", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "right")))),
                        node("join", NodeType.VARIABLE_AGGREGATOR, "聚合", Map.of("groups", List.of(
                                Map.of("outputVariable", "first", "sourceVariables", List.of("{{nodes.left.value}}", "{{nodes.right.value}}"), "strategy", "FIRST_NON_NULL"),
                                Map.of("outputVariable", "last", "sourceVariables", List.of("{{nodes.left.value}}", "{{nodes.right.value}}"), "strategy", "LAST_NON_NULL"),
                                Map.of("outputVariable", "merged", "sourceVariables", List.of("{{nodes.left.value}}", "{{nodes.right.value}}"), "strategy", "MERGE_TO_ARRAY")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "first", "value", "{{nodes.join.first}}"),
                                Map.of("name", "last", "value", "{{nodes.join.last}}"),
                                Map.of("name", "merged", "value", "{{nodes.join.merged}}"))))),
                List.of(edge("start", "left"), edge("start", "right"), edge("left", "join"), edge("right", "join"), edge("join", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.outputs()).containsEntry("first", "left");
        assertThat(result.outputs()).containsEntry("last", "right");
        assertThat(result.outputs()).containsEntry("merged", List.of("left", "right"));
    }

    @Test
    @DisplayName("adapter 返回的分支控制信号会过滤下游边")
    void executeRoutesAdapterBranchResult() {
        WorkflowNodeAdapter adapter = (node, scope, executionContext) -> {
            if (node.type() == NodeType.QUESTION_CLASSIFIER) {
                scope.write(node.scopeKey("selectedBranch"), "billing");
                return WorkflowNodeAdapter.Result.branch("billing");
            }
            return WorkflowNodeAdapter.Result.completed();
        };
        WorkflowRuntimeExecutor branchExecutor = new WorkflowRuntimeExecutor(adapter);
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("classifier", NodeType.QUESTION_CLASSIFIER, "分类", Map.of("branches", List.of(
                                Map.of("id", "tech", "name", "技术"),
                                Map.of("id", "billing", "name", "账单")))),
                        node("techNode", NodeType.VARIABLE_ASSIGNER, "技术", Map.of("assignments", List.of(
                                Map.of("variableName", "result", "type", "LITERAL", "value", "tech")))),
                        node("billingNode", NodeType.VARIABLE_ASSIGNER, "账单", Map.of("assignments", List.of(
                                Map.of("variableName", "result", "type", "LITERAL", "value", "billing")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.billingNode.result}}"))))),
                List.of(edge("start", "classifier"), edge("classifier", "branch:tech", "techNode"),
                        edge("classifier", "branch:billing", "billingNode"), edge("techNode", "end"),
                        edge("billingNode", "end")));

        WorkflowExecutionResult result = branchExecutor.execute(compiler.compile(graph), Map.of("question", "pay"));

        assertThat(result.success()).isTrue();
        assertThat(result.executedNodeIds()).containsExactly("start", "classifier", "billingNode", "end");
        assertThat(result.scope().has("nodes.techNode.result")).isFalse();
        assertThat(result.outputs()).containsEntry("answer", "billing");
    }

    @Test
    @DisplayName("多个上游汇合节点会等待所有已选择上游完成后再执行")
    void executeJoinWaitsForAllIncomingBranches() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("left", NodeType.VARIABLE_ASSIGNER, "左", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "L")))),
                        node("right", NodeType.VARIABLE_ASSIGNER, "右", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "R")))),
                        node("join", NodeType.VARIABLE_AGGREGATOR, "汇合", Map.of("groups", List.of(
                                Map.of("outputVariable", "joined", "sourceVariables", List.of("{{nodes.left.value}}", "{{nodes.right.value}}"), "strategy", "MERGE_TO_ARRAY")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.join.joined}}"))))),
                List.of(edge("start", "left"), edge("start", "right"), edge("left", "join"),
                        edge("right", "join"), edge("join", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.executedNodeIds()).containsExactly("start", "left", "right", "join", "end");
        assertThat(result.outputs()).containsEntry("answer", List.of("L", "R"));
    }

    @Test
    @DisplayName("缺少 adapter 时非确定性节点必须失败而不是伪成功跳过")
    void executeFailsWhenAdapterNodeCannotBeExecuted() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("llm", NodeType.LLM, "LLM"),
                        node("end", NodeType.END, "结束")),
                List.of(edge("start", "llm"), edge("llm", "end")));

        WorkflowExecutionResult result = executor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("未绑定工作流节点适配器");
        assertThat(result.executedNodeIds()).containsExactly("start", "llm");
    }

    @Test
    @DisplayName("LOOP 节点根据 adapter 的 continueLoop 信号选择循环或退出分支")
    void executeLoopRoutesByContinueLoopSignal() {
        WorkflowNodeAdapter adapter = (node, scope, executionContext) -> {
            if (node.type() == NodeType.LOOP) {
                scope.write(node.scopeKey("iteration"), 1);
                return WorkflowNodeAdapter.Result.loop(false);
            }
            return WorkflowNodeAdapter.Result.completed();
        };
        WorkflowRuntimeExecutor loopExecutor = new WorkflowRuntimeExecutor(adapter);
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("loop", NodeType.LOOP, "循环", Map.of("maxIterations", 3)),
                        node("body", NodeType.VARIABLE_ASSIGNER, "循环体", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "body")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "iteration", "value", "{{nodes.loop.iteration}}"))))),
                List.of(edge("start", "loop"), edge("loop", "branch:loop", "body"), edge("body", "loop"),
                        edge("loop", "branch:exit", "end")));

        WorkflowExecutionResult result = loopExecutor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.executedNodeIds()).containsExactly("start", "loop", "end");
        assertThat(result.scope().has("nodes.body.value")).isFalse();
        assertThat(result.outputs()).containsEntry("iteration", 1);
    }

    @Test
    @DisplayName("LOOP 节点返回继续循环时会重新进入循环体并最终退出")
    void executeLoopCanReenterBodyUntilAdapterSignalsExit() {
        WorkflowNodeAdapter adapter = (node, scope, executionContext) -> {
            if (node.type() == NodeType.LOOP) {
                Object current = scope.read("nodes.loop.iteration");
                int iteration = current instanceof Number number ? number.intValue() : 0;
                scope.write(node.scopeKey("iteration"), iteration + 1);
                return WorkflowNodeAdapter.Result.loop(iteration < 2);
            }
            return WorkflowNodeAdapter.Result.completed();
        };
        WorkflowRuntimeExecutor loopExecutor = new WorkflowRuntimeExecutor(adapter);
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("init", NodeType.VARIABLE_ASSIGNER, "初始化", Map.of("assignments", List.of(
                                Map.of("variableName", "iteration", "type", "LITERAL", "value", 0)))),
                        node("loop", NodeType.LOOP, "循环", Map.of("maxIterations", 3)),
                        node("body", NodeType.VARIABLE_ASSIGNER, "循环体", Map.of("assignments", List.of(
                                Map.of("variableName", "last", "type", "VARIABLE", "value", "{{nodes.loop.iteration}}")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "iteration", "value", "{{nodes.loop.iteration}}"),
                                Map.of("name", "lastBodyIteration", "value", "{{nodes.body.last}}"))))),
                List.of(edge("start", "init"), edge("init", "loop"), edge("loop", "branch:loop", "body"),
                        edge("body", "loop"), edge("loop", "branch:exit", "end")));

        WorkflowExecutionResult result = loopExecutor.execute(compiler.compile(graph), Map.of());

        assertThat(result.success()).isTrue();
        assertThat(result.executedNodeIds()).containsExactly("start", "init", "loop", "body", "loop", "body", "loop", "end");
        assertThat(result.outputs()).containsEntry("iteration", 3);
        assertThat(result.outputs()).containsEntry("lastBodyIteration", 2);
    }

    @Test
    @DisplayName("执行结果包含可恢复快照，续跑时不会重复执行已完成节点")
    void executeCanResumeFromSnapshotWithoutReplayingCompletedNodes() {
        WorkflowNodeAdapter pausingAdapter = (node, scope, executionContext) -> {
            if (node.id().equals("pause")) {
                throw new IllegalStateException("manual pause");
            }
            return WorkflowNodeAdapter.Result.completed();
        };
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始"),
                        node("first", NodeType.VARIABLE_ASSIGNER, "第一步", Map.of("assignments", List.of(
                                Map.of("variableName", "value", "type", "LITERAL", "value", "done")))),
                        node("pause", NodeType.LLM, "暂停点"),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.first.value}}"))))),
                List.of(edge("start", "first"), edge("first", "pause"), edge("pause", "end")));
        CompiledWorkflow compiled = compiler.compile(graph);

        WorkflowExecutionResult paused = new WorkflowRuntimeExecutor(pausingAdapter)
                .execute(compiled, Map.of(), ExecutionContext.builder().executionId("exec-1").build());
        WorkflowExecutionResult resumed = new WorkflowRuntimeExecutor((node, scope, executionContext) -> WorkflowNodeAdapter.Result.completed())
                .resume(compiled, paused.snapshot(), ExecutionContext.builder().executionId("exec-1").build());

        assertThat(paused.success()).isFalse();
        assertThat(paused.snapshot().scope()).containsEntry("nodes.first.value", "done");
        assertThat(resumed.success()).isTrue();
        assertThat(resumed.executedNodeIds()).containsExactly("pause", "end");
        assertThat(resumed.outputs()).containsEntry("answer", "done");
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
