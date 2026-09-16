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

import com.microservice.platform.ai.core.enums.AiEnumUtils;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.enums.WorkflowCompareOperator;
import com.microservice.platform.ai.core.workflow.runtime.adapter.WorkflowNodeAdapter;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.util.CompareUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 负责确定性工作流语义执行，并把智能体节点委托给适配器边界。
 *
 * @author xJh
 * @since 2026/05/24
 */
@Component
public class WorkflowRuntimeExecutor {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * IF_ELSE 未命中任何条件分支且无 ELSE 分支时返回的哨兵值。
     * 该值不会匹配任何出边句柄（branch:xxx），从而安全终止该路径。
     */
    private static final String NO_BRANCH_SELECTED = "__no_branch_selected__";

    private final WorkflowNodeAdapter nodeAdapter;

    public WorkflowRuntimeExecutor() {
        this.nodeAdapter = null;
    }

    @Autowired
    public WorkflowRuntimeExecutor(ObjectProvider<WorkflowNodeAdapter> nodeAdapterProvider) {
        this.nodeAdapter = nodeAdapterProvider.getIfAvailable();
    }

    WorkflowRuntimeExecutor(WorkflowNodeAdapter nodeAdapter) {
        this.nodeAdapter = nodeAdapter;
    }

    public WorkflowExecutionResult execute(CompiledWorkflow workflow, Map<String, Object> inputs) {
        return execute(workflow, inputs, null);
    }

    public WorkflowExecutionResult execute(CompiledWorkflow workflow, Map<String, Object> inputs,
                                           ExecutionContext executionContext) {
        WorkflowExecutionScope scope = new WorkflowExecutionScope();
        if (inputs != null) {
            inputs.forEach((key, value) -> scope.write("inputs." + key, value));
        }

        List<String> executedNodeIds = new ArrayList<>();
        Set<String> executedNodeSet = new LinkedHashSet<>();
        Map<String, Object> outputs = new HashMap<>();
        ExecutionState state = ExecutionState.start(workflow);
        try {
            executePlan(workflow, scope, outputs, executedNodeIds, executedNodeSet, executionContext, state);
            return WorkflowExecutionResult.success(scope, outputs, executedNodeIds, state.snapshot(scope, executedNodeSet));
        } catch (Exception e) {
            notifyNodeFailed(executionContext, state.currentNode.nodeId, e.getMessage());
            return WorkflowExecutionResult.failure(scope, e.getMessage(), executedNodeIds, state.retrySnapshot(scope, executedNodeSet));
        }
    }

    public WorkflowExecutionResult resume(CompiledWorkflow workflow, WorkflowExecutionSnapshot snapshot,
                                          ExecutionContext executionContext) {
        WorkflowExecutionScope scope = WorkflowExecutionScope.from(snapshot == null ? Map.of() : snapshot.scope());
        List<String> executedNodeIds = new ArrayList<>();
        Set<String> executedNodeSet = new LinkedHashSet<>(snapshot == null ? Set.of() : snapshot.executedNodeIds());
        Map<String, Object> outputs = new HashMap<>();
        ExecutionState state = ExecutionState.resume(snapshot);
        try {
            executePlan(workflow, scope, outputs, executedNodeIds, executedNodeSet, executionContext, state);
            return WorkflowExecutionResult.success(scope, outputs, executedNodeIds, state.snapshot(scope, executedNodeSet));
        } catch (Exception e) {
            notifyNodeFailed(executionContext, state.currentNode.nodeId, e.getMessage());
            return WorkflowExecutionResult.failure(scope, e.getMessage(), executedNodeIds, state.retrySnapshot(scope, executedNodeSet));
        }
    }

    private void notifyNodeFailed(ExecutionContext executionContext, String nodeId, String message) {
        if (executionContext != null && nodeId != null) {
            executionContext.pushNodeFailed(nodeId, message == null ? "智能体节点执行失败" : message);
        }
    }

    private void executePlan(CompiledWorkflow workflow, WorkflowExecutionScope scope, Map<String, Object> outputs,
                             List<String> executedNodeIds, Set<String> executedNodeSet,
                             ExecutionContext executionContext, ExecutionState state) {
        Map<String, List<CompiledWorkflow.CompiledEdge>> incomingEdges = incomingEdges(workflow);
        Set<String> repeatableNodeIds = repeatableLoopNodeIds(workflow);

        while (!state.pendingNodeIds().isEmpty()) {
            String nodeId = state.pendingNodeIds().removeFirst();
            CompiledWorkflow.CompiledNode node = workflow.node(nodeId);
            if (node == null || (executedNodeSet.contains(node.id()) && !repeatableNodeIds.contains(node.id()))) {
                continue;
            }
            if (!readyToExecute(node, incomingEdges, state.activatedIncomingSources(), executedNodeSet)) {
                state.pendingNodeIds().addLast(node.id());
                if (isDeadlocked(state.pendingNodeIds(), workflow, incomingEdges, state.activatedIncomingSources(), executedNodeSet)) {
                    throw new IllegalStateException("智能体工作流无法继续推进，请检查汇合节点和分支句柄");
                }
                continue;
            }
            beforeNode(node, scope, executionContext);
            state.currentNodeId(node.id());
            executedNodeSet.add(node.id());
            executedNodeIds.add(node.id());

            String selectedBranch = executeSingleNode(node, scope, outputs, executionContext);
            afterNode(node, scope, executionContext);
            for (CompiledWorkflow.CompiledEdge edge : nextEdges(node, workflow, selectedBranch)) {
                state.activatedIncomingSources().computeIfAbsent(edge.target(), ignored -> new LinkedHashSet<>()).add(edge.source());
                state.pendingNodeIds().addLast(edge.target());
            }
            state.currentNodeId(null);
        }
    }

    private String executeSingleNode(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope,
                                     Map<String, Object> outputs, ExecutionContext executionContext) {
        if (node.type() == NodeType.START) {
            scope.write(node.scopeKey("started"), true);
            return null;
        }
        if (node.type() == NodeType.VARIABLE_ASSIGNER) {
            executeVariableAssigner(node, scope);
            return null;
        }
        if (node.type() == NodeType.IF_ELSE) {
            String selectedBranch = selectIfElseBranch(node, scope);
            scope.write(node.scopeKey("selectedBranch"), selectedBranch);
            return selectedBranch;
        }
        if (node.type() == NodeType.VARIABLE_AGGREGATOR) {
            executeVariableAggregator(node, scope);
            return null;
        }
        if (node.type() == NodeType.PARALLEL) {
            scope.write(node.scopeKey("started"), true);
            return null;
        }
        if (node.type() == NodeType.END) {
            outputs.putAll(resolveEndOutputs(node, scope));
            return null;
        }
        return executeWithAdapter(node, scope, executionContext);
    }

    private void beforeNode(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope,
                            ExecutionContext executionContext) {
        if (executionContext == null) {
            return;
        }
        if (executionContext.shouldStop()) {
            throw new IllegalStateException("工作流执行已取消: " + executionContext.getExecutionId());
        }
        executionContext.setCurrentNodeId(node.id());
        waitIfPaused(executionContext);
        if (executionContext.isBreakpoint(node.id())) {
            executionContext.setPaused(true);
            executionContext.pushBreakpointHit(node.id());
            waitIfPaused(executionContext);
        }
        executionContext.pushNodeStarted(node.id(), scope.state());
    }

    private void waitIfPaused(ExecutionContext executionContext) {
        try {
            executionContext.waitIfPaused();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("工作流执行被中断: " + executionContext.getExecutionId(), e);
        }
    }

    private void afterNode(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope,
                           ExecutionContext executionContext) {
        if (executionContext == null) {
            return;
        }
        Map<String, Object> output = scopedNodeOutput(node, scope);
        executionContext.setNodeOutput(node.id(), output);
        executionContext.pushNodeCompleted(node.id(), output, asLong(scope.read(node.scopeKey("duration"))));
    }

    private String executeWithAdapter(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope,
                                      ExecutionContext executionContext) {
        if (nodeAdapter == null) {
            throw new IllegalStateException("节点未绑定工作流节点适配器: " + node.id());
        }
        WorkflowNodeAdapter.Result result = nodeAdapter.execute(node, scope, executionContext);
        if (result == null) {
            return null;
        }
        if (result.continueLoop() != null) {
            scope.write(node.scopeKey("continueLoop"), result.continueLoop());
            return result.continueLoop() ? "loop" : "exit";
        }
        if (result.selectedBranch() != null) {
            scope.write(node.scopeKey("selectedBranch"), result.selectedBranch());
            return result.selectedBranch();
        }
        Object selectedBranch = scope.read(node.scopeKey("selectedBranch"));
        return selectedBranch == null ? null : String.valueOf(selectedBranch);
    }

    private Map<String, Object> scopedNodeOutput(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope) {
        String prefix = node.scopeKey("");
        Map<String, Object> output = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : scope.state().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                output.put(entry.getKey().substring(prefix.length()), entry.getValue());
            }
        }
        return output;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private boolean readyToExecute(CompiledWorkflow.CompiledNode node,
                                   Map<String, List<CompiledWorkflow.CompiledEdge>> incomingEdges,
                                   Map<String, Set<String>> activatedIncomingSources,
                                   Set<String> executedNodeSet) {
        Set<String> activatedSources = activatedIncomingSources.getOrDefault(node.id(), Set.of());
        if (activatedSources.isEmpty()) {
            return incomingEdges.getOrDefault(node.id(), List.of()).isEmpty();
        }
        return activatedSources.stream().allMatch(executedNodeSet::contains);
    }

    private boolean isDeadlocked(Deque<String> pending,
                                 CompiledWorkflow workflow,
                                 Map<String, List<CompiledWorkflow.CompiledEdge>> incomingEdges,
                                 Map<String, Set<String>> activatedIncomingSources,
                                 Set<String> executedNodeSet) {
        return pending.stream()
                .map(workflow::node)
                .noneMatch(node -> readyToExecute(node, incomingEdges, activatedIncomingSources, executedNodeSet));
    }

    private Map<String, List<CompiledWorkflow.CompiledEdge>> incomingEdges(CompiledWorkflow workflow) {
        Map<String, List<CompiledWorkflow.CompiledEdge>> incoming = new HashMap<>();
        for (List<CompiledWorkflow.CompiledEdge> edges : workflow.outgoingEdges().values()) {
            for (CompiledWorkflow.CompiledEdge edge : edges) {
                incoming.computeIfAbsent(edge.target(), ignored -> new ArrayList<>()).add(edge);
            }
        }
        incoming.replaceAll((ignored, edges) -> Collections.unmodifiableList(edges));
        return incoming;
    }

    private Set<String> repeatableLoopNodeIds(CompiledWorkflow workflow) {
        Set<String> repeatable = new LinkedHashSet<>();
        for (CompiledWorkflow.CompiledNode node : workflow.nodes().values()) {
            if (node.type() == NodeType.LOOP || node.type() == NodeType.ITERATION) {
                repeatable.add(node.id());
                collectLoopBodyNodes(workflow, node.id(), repeatable);
            }
        }
        return repeatable;
    }

    private void collectLoopBodyNodes(CompiledWorkflow workflow, String loopNodeId, Set<String> repeatable) {
        Deque<String> pending = new ArrayDeque<>();
        for (CompiledWorkflow.CompiledEdge edge : workflow.outgoingEdges().getOrDefault(loopNodeId, List.of())) {
            if (isLoopBodyHandle(edge.sourceHandle())) {
                pending.add(edge.target());
            }
        }
        while (!pending.isEmpty()) {
            String nodeId = pending.removeFirst();
            if (loopNodeId.equals(nodeId) || !repeatable.add(nodeId)) {
                continue;
            }
            for (CompiledWorkflow.CompiledEdge edge : workflow.outgoingEdges().getOrDefault(nodeId, List.of())) {
                pending.add(edge.target());
            }
        }
    }

    private boolean isLoopBodyHandle(String sourceHandle) {
        return "branch:loop".equals(sourceHandle);
    }

    @SuppressWarnings("unchecked")
    private void executeVariableAssigner(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope) {
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
            Object value = resolveAssignmentValue(map, scope);
            value = convertValue(value, Objects.toString(map.get("variableType"), ""));
            scope.write(node.scopeKey(String.valueOf(variableName)), value);
        }
    }

    private Object resolveAssignmentValue(Map<?, ?> assignment, WorkflowExecutionScope scope) {
        Object rawValue = assignment.get("value");
        String type = Objects.toString(assignment.get("type"), "");
        if (type.isBlank()) {
            throw new IllegalArgumentException("变量赋值缺少 type 配置");
        }
        if ("VARIABLE".equalsIgnoreCase(type)) {
            return rawValue instanceof String text ? resolveVariableReference(text, scope) : rawValue;
        }
        if ("EXPRESSION".equalsIgnoreCase(type)) {
            Object resolved = rawValue instanceof String text ? resolveTemplate(text, scope) : rawValue;
            Object transformExpression = assignment.get("transformExpression");
            return transformExpression instanceof String expression && !expression.isBlank()
                    ? resolveTemplate(expression, scope)
                    : resolved;
        }
        if ("LITERAL".equalsIgnoreCase(type)) {
            return rawValue;
        }
        throw new IllegalArgumentException("未知变量赋值类型: " + type);
    }

    private Object resolveVariableReference(String reference, WorkflowExecutionScope scope) {
        String key = reference.trim();
        if (key.startsWith("{{") && key.endsWith("}}")) {
            key = key.substring(2, key.length() - 2).trim();
        }
        return scope.read(key);
    }

    private Object convertValue(Object value, String variableType) {
        if (value == null || variableType.isBlank() || "any".equalsIgnoreCase(variableType)) {
            return value;
        }
        return switch (variableType.toLowerCase()) {
            case "string" -> String.valueOf(value);
            case "number" -> toNumber(value);
            case "boolean" -> toBoolean(value);
            case "array" -> value instanceof Collection<?> collection ? List.copyOf(collection) : List.of(value);
            default -> value;
        };
    }

    private Number toNumber(Object value) {
        if (value instanceof Number number) {
            return number;
        }
        String text = String.valueOf(value).trim();
        if (text.matches("^-?\\d+$")) {
            return Long.parseLong(text);
        }
        return text.contains(".") ? Double.parseDouble(text) : Long.parseLong(text);
    }

    private Boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private void executeVariableAggregator(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope) {
        Object groups = node.config().get("groups");
        if (!(groups instanceof Collection<?> collection)) {
            return;
        }
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> group)) {
                continue;
            }
            String outputVariable = Objects.toString(group.get("outputVariable"), "");
            if (outputVariable.isBlank()) {
                continue;
            }
            Object variables = group.get("sourceVariables");
            if (!(variables instanceof Collection<?> variableNames)) {
                continue;
            }
            List<Object> values = new ArrayList<>();
            for (Object variable : variableNames) {
                Object value = resolveVariableReference(Objects.toString(variable, ""), scope);
                if (value != null) {
                    values.add(value);
                }
            }
            Object aggregated = aggregateValues(group, values);
            scope.write(node.scopeKey(outputVariable), aggregated);
        }
    }

    private Object aggregateValues(Map<?, ?> group, List<Object> values) {
        String strategy = Objects.toString(group.get("strategy"), "FIRST_NON_NULL");
        if ("FIRST_NON_NULL".equalsIgnoreCase(strategy)) {
            return values.isEmpty() ? null : values.getFirst();
        }
        if ("LAST_NON_NULL".equalsIgnoreCase(strategy)) {
            return values.isEmpty() ? null : values.getLast();
        }
        if ("MERGE_TO_ARRAY".equalsIgnoreCase(strategy)) {
            return List.copyOf(values);
        }
        if ("MERGE_OBJECTS".equalsIgnoreCase(strategy)) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Object value : values) {
                if (value instanceof Map<?, ?> source) {
                    source.forEach((key, sourceValue) -> map.put(String.valueOf(key), sourceValue));
                } else {
                    throw new IllegalArgumentException("MERGE_OBJECTS 只支持对象源变量");
                }
            }
            return map;
        }
        throw new IllegalArgumentException("未知变量聚合策略: " + strategy);
    }

    private String selectIfElseBranch(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope) {
        Object branches = node.config().get("branches");
        if (!(branches instanceof Collection<?> collection)) {
            return NO_BRANCH_SELECTED;
        }
        String elseBranch = null;
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> branch)) {
                continue;
            }
            String id = Objects.toString(branch.get("id"), null);
            String type = Objects.toString(branch.get("type"), "");
            if ("ELSE".equalsIgnoreCase(type)) {
                elseBranch = id;
                continue;
            }
            if (matchesConditions(branch, scope)) {
                return id;
            }
        }
        // 没有命中任何条件分支：有 ELSE 走 ELSE，否则返回哨兵值，
        // 使 nextEdges 过滤后为空、终止该路径，而不是激活全部出边（then/else 同时执行）。
        return elseBranch != null ? elseBranch : NO_BRANCH_SELECTED;
    }

    /**
     * 评估单个分支的条件组。
     * 支持完整比较运算符集（委托 {@link CompareUtils}）与分支级 AND/OR 逻辑组合，
     * 并支持比较值本身为变量引用（valueIsVariable=true）。
     * @param branch branch 参数
     * @param scope scope 参数
     * @return 处理结果
     */
    private boolean matchesConditions(Map<?, ?> branch, WorkflowExecutionScope scope) {
        Object conditions = branch.get("conditions");
        if (!(conditions instanceof Collection<?> collection) || collection.isEmpty()) {
            return false;
        }
        boolean orLogic = "OR".equalsIgnoreCase(Objects.toString(branch.get("operator"), "AND"));
        boolean result = !orLogic;
        for (Object item : collection) {
            if (!(item instanceof Map<?, ?> condition)) {
                continue;
            }
            boolean matched = evaluateCondition(condition, scope);
            if (orLogic) {
                if (matched) {
                    return true;
                }
            } else if (!matched) {
                return false;
            }
        }
        return result;
    }

    private boolean evaluateCondition(Map<?, ?> condition, WorkflowExecutionScope scope) {
        Object actual = scope.read(Objects.toString(condition.get("variable"), ""));
        Object expected = resolveConditionValue(condition, scope);
        WorkflowCompareOperator operator = parseCompareOperator(condition.get("operator"));
        return CompareUtils.compare(actual, expected, operator);
    }

    private Object resolveConditionValue(Map<?, ?> condition, WorkflowExecutionScope scope) {
        Object value = condition.get("value");
        if (Boolean.TRUE.equals(condition.get("valueIsVariable")) && value != null) {
            return scope.read(String.valueOf(value));
        }
        return value;
    }

    private WorkflowCompareOperator parseCompareOperator(Object operator) {
        if (operator instanceof WorkflowCompareOperator op) {
            return op;
        }
        WorkflowCompareOperator op = AiEnumUtils.fromCode(WorkflowCompareOperator.class, Objects.toString(operator, null));
        return op != null ? op : WorkflowCompareOperator.EQUALS;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> resolveEndOutputs(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope) {
        Object configuredOutputs = node.config().get("outputs");
        if (!(configuredOutputs instanceof Collection<?> collection)) {
            return Map.of();
        }

        Map<String, Object> outputs = new HashMap<>();
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
            scope.write(node.scopeKey(String.valueOf(name)), value);
        }
        return outputs;
    }

    private List<CompiledWorkflow.CompiledEdge> nextEdges(CompiledWorkflow.CompiledNode node,
                                                          CompiledWorkflow workflow,
                                                          String selectedBranch) {
        List<CompiledWorkflow.CompiledEdge> edges = workflow.outgoingEdges().getOrDefault(node.id(), List.of());
        if (selectedBranch == null || selectedBranch.isBlank()) {
            return edges;
        }
        return edges.stream()
                .filter(edge -> ("branch:" + selectedBranch).equals(edge.sourceHandle()))
                .toList();
    }

    private record ExecutionState(Deque<String> pendingNodeIds,
                                  Map<String, Set<String>> activatedIncomingSources,
                                  CurrentNode currentNode) {

        static ExecutionState start(CompiledWorkflow workflow) {
            Deque<String> pending = new ArrayDeque<>();
            pending.add(workflow.startNode().id());
            return new ExecutionState(pending, new HashMap<>(), new CurrentNode());
        }

        static ExecutionState resume(WorkflowExecutionSnapshot snapshot) {
            Deque<String> pending = new ArrayDeque<>(snapshot == null ? List.of() : snapshot.pendingNodeIds());
            Map<String, Set<String>> activated = new HashMap<>();
            if (snapshot != null) {
                snapshot.activatedIncomingSources().forEach((key, value) -> activated.put(key, new LinkedHashSet<>(value)));
            }
            return new ExecutionState(pending, activated, new CurrentNode());
        }

        void currentNodeId(String nodeId) {
            currentNode.nodeId = nodeId;
        }

        WorkflowExecutionSnapshot snapshot(WorkflowExecutionScope scope, Set<String> executedNodeSet) {
            return snapshot(scope, executedNodeSet, false);
        }

        private WorkflowExecutionSnapshot snapshot(WorkflowExecutionScope scope, Set<String> executedNodeSet,
                                                   boolean retryCurrentNode) {
            Map<String, Set<String>> activated = activatedIncomingSources.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> Set.copyOf(entry.getValue())));
            Deque<String> pending = new ArrayDeque<>(pendingNodeIds);
            Set<String> executed = new LinkedHashSet<>(executedNodeSet);
            if (retryCurrentNode && currentNode.nodeId != null) {
                pending.addFirst(currentNode.nodeId);
                executed.remove(currentNode.nodeId);
            }
            return new WorkflowExecutionSnapshot(scope.state(), List.copyOf(pending), Set.copyOf(executed), activated);
        }

        WorkflowExecutionSnapshot retrySnapshot(WorkflowExecutionScope scope, Set<String> executedNodeSet) {
            return snapshot(scope, executedNodeSet, true);
        }
    }

    private static final class CurrentNode {

        private String nodeId;
    }

    private Object resolveTemplate(String template, WorkflowExecutionScope scope) {
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        boolean wholeValueReference = template.trim().matches("^\\{\\{[^}]+}}$");
        Object wholeValue = null;
        while (matcher.find()) {
            String key = matcher.group(1).trim();
            Object value = scope.read(key);
            if (wholeValueReference) {
                wholeValue = value;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value == null ? "" : String.valueOf(value)));
        }
        matcher.appendTail(result);
        return wholeValueReference ? wholeValue : result.toString();
    }
}
