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
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将持久化的可视化图编译为 LangChain4j 智能体执行计划。
 *
 * @author xJh
 * @since 2026/05/24
 */
@Component
public class WorkflowCompiler {

    private final LangChain4jWorkflowFactory workflowFactory;

    public WorkflowCompiler() {
        this(new LangChain4jWorkflowFactory());
    }

    public WorkflowCompiler(LangChain4jWorkflowFactory workflowFactory) {
        this.workflowFactory = workflowFactory;
    }

    public CompiledWorkflow compile(WorkflowGraph graph) {
        if (graph == null || graph.getNodes() == null || graph.getNodes().isEmpty()) {
            throw new IllegalArgumentException("工作流图必须包含节点");
        }

        Map<String, WorkflowNode> rawNodes = graph.getNodes().stream()
                .collect(Collectors.toMap(WorkflowNode::getId, node -> node, (left, right) -> left, LinkedHashMap::new));
        List<WorkflowEdge> rawEdges = graph.getEdges() == null ? List.of() : graph.getEdges();
        validateEdges(rawNodes.keySet(), rawEdges);

        List<WorkflowNode> starts = graph.getNodes().stream()
                .filter(node -> node.getType() == NodeType.START)
                .toList();
        if (starts.size() != 1) {
            throw new IllegalArgumentException("智能体工作流必须且只能包含一个 START 节点");
        }

        List<WorkflowNode> terminals = graph.getNodes().stream()
                .filter(node -> node.getType() == NodeType.END)
                .toList();
        if (terminals.isEmpty()) {
            throw new IllegalArgumentException("智能体工作流至少需要一个终止 END 节点");
        }

        validateWorkflowNodeConfigs(graph.getNodes(), rawEdges);
        validateReachability(starts.get(0).getId(), rawNodes.keySet(), rawEdges);
        validateAcyclicOutsideLoop(rawNodes, rawEdges);

        Map<String, CompiledWorkflow.CompiledNode> compiledNodes = new LinkedHashMap<>();
        for (WorkflowNode node : graph.getNodes()) {
            compiledNodes.put(node.getId(), compileNode(node));
        }

        Map<String, List<CompiledWorkflow.CompiledEdge>> outgoingEdges = rawEdges.stream()
                .map(this::compileEdge)
                .collect(Collectors.groupingBy(
                        CompiledWorkflow.CompiledEdge::source,
                        LinkedHashMap::new,
                        Collectors.toUnmodifiableList()));

        CompiledWorkflow compiled = new CompiledWorkflow(
                compiledNodes.get(starts.get(0).getId()),
                terminals.stream().map(node -> compiledNodes.get(node.getId())).toList(),
                topologicalOrder(starts.get(0).getId(), compiledNodes, outgoingEdges),
                Collections.unmodifiableMap(compiledNodes),
                Collections.unmodifiableMap(outgoingEdges),
                null);
        return compiled.withWorkflowModel(workflowFactory.buildModel(compiled));
    }

    private CompiledWorkflow.CompiledNode compileNode(WorkflowNode node) {
        Map<String, Object> config = node.getData() == null ? Map.of() : Map.copyOf(node.getData());
        return new CompiledWorkflow.CompiledNode(node.getId(), node.getType(), node.getLabel(), config);
    }

    private CompiledWorkflow.CompiledEdge compileEdge(WorkflowEdge edge) {
        return new CompiledWorkflow.CompiledEdge(edge.getId(), edge.getSource(), edge.getSourceHandle(),
                edge.getTarget(), edge.getTargetHandle());
    }

    private void validateEdges(Set<String> nodeIds, List<WorkflowEdge> edges) {
        for (WorkflowEdge edge : edges) {
            if (!nodeIds.contains(edge.getSource())) {
                throw new IllegalArgumentException("连线引用了未知的源节点: " + edge.getSource());
            }
            if (!nodeIds.contains(edge.getTarget())) {
                throw new IllegalArgumentException("连线引用了未知的目标节点: " + edge.getTarget());
            }
        }
    }

    private void validateWorkflowNodeConfigs(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        Map<String, WorkflowNode> nodeMap = nodes.stream().collect(Collectors.toMap(WorkflowNode::getId, node -> node));
        Map<String, List<WorkflowEdge>> outgoing = edges.stream().collect(Collectors.groupingBy(WorkflowEdge::getSource));
        for (WorkflowNode node : nodes) {
            if (node.getType() == NodeType.IF_ELSE || node.getType() == NodeType.QUESTION_CLASSIFIER) {
                validateBranchHandles(node, outgoing.getOrDefault(node.getId(), List.of()));
            }
            if (node.getType() == NodeType.LOOP) {
                validateLoop(node);
            }
            if (node.getType() == NodeType.START && hasIncoming(node.getId(), edges)) {
                throw new IllegalArgumentException("START 节点不能存在入边: " + node.getId());
            }
            if (node.getType() == NodeType.END && outgoing.containsKey(node.getId())) {
                throw new IllegalArgumentException("END 节点不能存在出边: " + node.getId());
            }
            if (node.getType() == NodeType.PARALLEL
                    && outgoing.getOrDefault(node.getId(), List.of()).size() < 2) {
                throw new IllegalArgumentException("PARALLEL 节点至少需要两个分支: " + node.getId());
            }
            if (!nodeMap.containsKey(node.getId())) {
                throw new IllegalArgumentException("未知节点: " + node.getId());
            }
        }
    }

    private void validateReachability(String startNodeId, Set<String> nodeIds, List<WorkflowEdge> edges) {
        Map<String, List<WorkflowEdge>> outgoing = edges.stream().collect(Collectors.groupingBy(WorkflowEdge::getSource));
        Set<String> reachable = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!reachable.add(nodeId)) {
                continue;
            }
            for (WorkflowEdge edge : outgoing.getOrDefault(nodeId, List.of())) {
                queue.add(edge.getTarget());
            }
        }
        Set<String> unreachable = new LinkedHashSet<>(nodeIds);
        unreachable.removeAll(reachable);
        if (!unreachable.isEmpty()) {
            throw new IllegalArgumentException("智能体工作流包含不可达节点: " + unreachable);
        }
    }

    private boolean hasIncoming(String nodeId, Collection<WorkflowEdge> edges) {
        return edges.stream().anyMatch(edge -> Objects.equals(edge.getTarget(), nodeId));
    }

    private void validateBranchHandles(WorkflowNode node, List<WorkflowEdge> outgoing) {
        Object branches = node.getData() == null ? null
                : node.getType() == NodeType.QUESTION_CLASSIFIER ? node.getData().get("categories") : node.getData().get("branches");
        if (!(branches instanceof Collection<?> branchCollection) || branchCollection.isEmpty()) {
            return;
        }

        Set<String> validBranchIds = new HashSet<>();
        for (Object branch : branchCollection) {
            if (branch instanceof Map<?, ?> branchMap) {
                Object id = branchMap.get("id");
                if (id != null) {
                    validBranchIds.add("branch:" + id);
                }
            }
        }

        for (WorkflowEdge edge : outgoing) {
            String handle = edge.getSourceHandle();
            if (handle != null && !handle.isBlank() && !validBranchIds.contains(handle)) {
                throw new IllegalArgumentException("节点 " + node.getId() + " 存在未知分支句柄: " + handle);
            }
        }
    }

    private void validateLoop(WorkflowNode node) {
        Object maxIterations = node.getData() == null ? null : node.getData().get("maxIterations");
        if (!(maxIterations instanceof Number number) || number.intValue() <= 0) {
            throw new IllegalArgumentException("LOOP 节点必须设置正数 maxIterations: " + node.getId());
        }
    }

    private void validateAcyclicOutsideLoop(Map<String, WorkflowNode> nodes, List<WorkflowEdge> edges) {
        Map<String, List<String>> outgoing = new HashMap<>();
        for (WorkflowEdge edge : edges) {
            WorkflowNode source = nodes.get(edge.getSource());
            if (source != null && source.getType() == NodeType.LOOP) {
                continue;
            }
            outgoing.computeIfAbsent(edge.getSource(), ignored -> new ArrayList<>()).add(edge.getTarget());
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (String nodeId : nodes.keySet()) {
            if (hasCycle(nodeId, outgoing, visiting, visited)) {
                throw new IllegalArgumentException("智能体工作流包含不受支持的环路，请使用 LOOP 节点表达: " + nodeId);
            }
        }
    }

    private boolean hasCycle(String nodeId, Map<String, List<String>> outgoing, Set<String> visiting, Set<String> visited) {
        if (visited.contains(nodeId)) {
            return false;
        }
        if (!visiting.add(nodeId)) {
            return true;
        }
        for (String target : outgoing.getOrDefault(nodeId, List.of())) {
            if (hasCycle(target, outgoing, visiting, visited)) {
                return true;
            }
        }
        visiting.remove(nodeId);
        visited.add(nodeId);
        return false;
    }

    private List<CompiledWorkflow.CompiledNode> topologicalOrder(
                                                                 String startNodeId,
                                                                 Map<String, CompiledWorkflow.CompiledNode> nodes,
                                                                 Map<String, List<CompiledWorkflow.CompiledEdge>> outgoingEdges) {
        List<CompiledWorkflow.CompiledNode> order = new ArrayList<>();
        Set<String> visited = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);

        while (!queue.isEmpty()) {
            String nodeId = queue.poll();
            if (!visited.add(nodeId)) {
                continue;
            }
            CompiledWorkflow.CompiledNode node = nodes.get(nodeId);
            if (node != null) {
                order.add(node);
            }
            for (CompiledWorkflow.CompiledEdge edge : outgoingEdges.getOrDefault(nodeId, List.of())) {
                queue.add(edge.target());
            }
        }

        return List.copyOf(order);
    }
}
