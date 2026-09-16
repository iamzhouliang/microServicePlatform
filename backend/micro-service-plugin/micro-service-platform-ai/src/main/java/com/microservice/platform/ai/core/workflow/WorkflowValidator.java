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

package com.microservice.platform.ai.core.workflow;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodeDefinition;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodeDefinitionCatalog;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodeConfigField;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowEdge;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工作流验证器
 * 验证工作流结构的有效性：
 * 1. 必须有且仅有一个 START 节点
 * 2. 必须至少有一个 END 节点
 * 3. 所有节点必须从 START 节点可达
 * 4. 边的连接必须有效（源节点和目标节点都存在）
 * 5. 不允许自环（节点连接到自己）
 *
 * @author xJh
 * @since 2026/01/07
 */
@Component
public class WorkflowValidator {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([^}]+?)\\s*}}");
    private static final int LOOP_WARNING_THRESHOLD = 50;
    private static final int PARALLEL_WARNING_THRESHOLD = 6;

    /**
     * 验证工作流图的完整性和有效性
     *
     * @param graph 工作流图
     * @return 验证结果
     */
    public WorkflowValidationResult validate(WorkflowGraph graph) {
        WorkflowValidationResult result = WorkflowValidationResult.builder()
                .valid(true)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();

        // 基础检查
        if (graph == null) {
            result.addError("工作流图不能为空");
            return result;
        }

        List<WorkflowNode> nodes = graph.getNodes();
        final List<WorkflowEdge> edges = graph.getEdges();

        // 检查节点列表
        if (nodes == null || nodes.isEmpty()) {
            result.addError("工作流必须包含至少一个节点");
            return result;
        }

        // 验证 START 节点
        result.merge(validateStartNode(nodes));

        // 验证 END 节点
        result.merge(validateEndNode(nodes));

        // 验证节点 ID 唯一性
        result.merge(validateNodeIdUniqueness(nodes));

        // 验证边的有效性
        result.merge(validateEdges(nodes, edges));

        // 验证 START 节点没有输入边
        result.merge(validateStartNodeNoInputEdges(nodes, edges));

        // 验证节点可达性（从 START 节点）
        result.merge(validateNodeReachability(nodes, edges));

        // 发布级 Workflow 诊断
        result.merge(validateTerminalEdges(nodes, edges));
        result.merge(validateBranchHandles(nodes, edges));
        result.merge(validateNodeConfigSchema(nodes));
        result.merge(validateNodeContracts(nodes));
        result.merge(validateVariableReferences(nodes));
        result.merge(validateRuntimeRisk(nodes, edges));

        return result;
    }

    /**
     * 验证 START 节点
     * 必须有且仅有一个 START 节点
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateStartNode(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        List<WorkflowNode> startNodes = nodes.stream()
                .filter(node -> node.getType() == NodeType.START)
                .toList();

        if (startNodes.isEmpty()) {
            result.addError("工作流必须包含一个 START 节点");
        } else if (startNodes.size() > 1) {
            result.addError("工作流只能包含一个 START 节点，当前有 " + startNodes.size() + " 个");
        }

        return result;
    }

    /**
     * 验证 START 节点没有输入边
     * START 节点作为工作流入口，不应该有任何输入边
     * @param edges edges 参数
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateStartNodeNoInputEdges(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        if (edges == null || edges.isEmpty()) {
            return result;
        }

        // 找到所有 START 节点的 ID
        Set<String> startNodeIds = nodes.stream()
                .filter(node -> node.getType() == NodeType.START)
                .map(WorkflowNode::getId)
                .collect(Collectors.toSet());

        // 检查是否有边指向 START 节点
        List<WorkflowEdge> edgesToStart = edges.stream()
                .filter(edge -> edge.getTarget() != null && startNodeIds.contains(edge.getTarget()))
                .toList();

        if (!edgesToStart.isEmpty()) {
            for (WorkflowEdge edge : edgesToStart) {
                result.addError("START 节点 [" + edge.getTarget() + "] 不能有输入边，但边 [" + edge.getId() + "] 指向了它");
            }
        }

        return result;
    }

    /**
     * 验证 END 节点
     * 必须至少有一个 END 节点
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateEndNode(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        List<WorkflowNode> endNodes = nodes.stream()
                .filter(node -> node.getType() == NodeType.END)
                .toList();

        if (endNodes.isEmpty()) {
            result.addError("工作流必须包含至少一个 END 节点");
        }

        return result;
    }

    /**
     * 验证节点 ID 唯一性
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateNodeIdUniqueness(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        Set<String> seenIds = new HashSet<>();
        List<String> duplicateIds = new ArrayList<>();

        for (WorkflowNode node : nodes) {
            if (node.getId() == null || node.getId().isBlank()) {
                result.addError("节点 ID 不能为空");
                continue;
            }
            if (!seenIds.add(node.getId())) {
                duplicateIds.add(node.getId());
            }
        }

        if (!duplicateIds.isEmpty()) {
            result.addError("存在重复的节点 ID: " + String.join(", ", duplicateIds));
        }

        return result;
    }

    /**
     * 验证边的有效性
     * 1. 源节点和目标节点必须存在
     * 2. 不允许自环
     * 3. 边 ID 必须唯一
     * @param edges edges 参数
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateEdges(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        if (edges == null || edges.isEmpty()) {
            // 如果只有一个节点且是 START 或 END，可以没有边
            if (nodes.size() > 1) {
                result.addWarning("工作流没有任何连接边");
            }
            return result;
        }

        Set<String> nodeIds = nodes.stream()
                .map(WorkflowNode::getId)
                .collect(Collectors.toSet());

        Set<String> seenEdgeIds = new HashSet<>();

        for (WorkflowEdge edge : edges) {
            // 检查边 ID
            if (edge.getId() == null || edge.getId().isBlank()) {
                result.addError("边 ID 不能为空");
                continue;
            }
            if (!seenEdgeIds.add(edge.getId())) {
                result.addError("存在重复的边 ID: " + edge.getId());
            }

            // 检查源节点
            if (edge.getSource() == null || edge.getSource().isBlank()) {
                result.addError("边 [" + edge.getId() + "] 的源节点不能为空");
            } else if (!nodeIds.contains(edge.getSource())) {
                result.addError("边 [" + edge.getId() + "] 的源节点 [" + edge.getSource() + "] 不存在");
            }

            // 检查目标节点
            if (edge.getTarget() == null || edge.getTarget().isBlank()) {
                result.addError("边 [" + edge.getId() + "] 的目标节点不能为空");
            } else if (!nodeIds.contains(edge.getTarget())) {
                result.addError("边 [" + edge.getId() + "] 的目标节点 [" + edge.getTarget() + "] 不存在");
            }

            // 检查自环
            if (edge.getSource() != null && edge.getSource().equals(edge.getTarget())) {
                result.addError("边 [" + edge.getId() + "] 不能连接到自身（自环）");
            }
        }

        return result;
    }

    /**
     * 验证节点可达性
     * 所有节点必须从 START 节点可达
     * @param edges edges 参数
     * @param nodes 节点集合
     * @return 处理结果
     */
    private WorkflowValidationResult validateNodeReachability(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();

        // 找到 START 节点
        Optional<WorkflowNode> startNodeOpt = nodes.stream()
                .filter(node -> node.getType() == NodeType.START)
                .findFirst();

        if (startNodeOpt.isEmpty()) {
            // 已经在 validateStartNode 中报错了
            return result;
        }

        String startNodeId = startNodeOpt.get().getId();

        // 构建邻接表
        Map<String, List<String>> adjacencyList = buildAdjacencyList(edges);

        // BFS 遍历找出所有可达节点
        Set<String> reachableNodes = findReachableNodes(startNodeId, adjacencyList);

        // 检查不可达的节点
        List<String> unreachableNodes = nodes.stream()
                .map(WorkflowNode::getId)
                .filter(id -> !reachableNodes.contains(id))
                .toList();

        if (!unreachableNodes.isEmpty()) {
            result.addWarning("以下节点从 START 节点不可达: " + String.join(", ", unreachableNodes));
        }

        return result;
    }

    /**
     * 构建邻接表
     * @param edges edges 参数
     * @return 处理结果
     */
    private Map<String, List<String>> buildAdjacencyList(List<WorkflowEdge> edges) {
        Map<String, List<String>> adjacencyList = new HashMap<>();

        if (edges == null) {
            return adjacencyList;
        }

        for (WorkflowEdge edge : edges) {
            if (edge.getSource() != null && edge.getTarget() != null) {
                adjacencyList.computeIfAbsent(edge.getSource(), k -> new ArrayList<>())
                        .add(edge.getTarget());
            }
        }

        return adjacencyList;
    }

    /**
     * 使用 BFS 找出从起始节点可达的所有节点
     * @param adjacencyList adjacencyList 参数
     * @param startNodeId startNodeId 参数
     * @return 处理结果
     */
    private Set<String> findReachableNodes(String startNodeId, Map<String, List<String>> adjacencyList) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.offer(startNodeId);
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<String> neighbors = adjacencyList.getOrDefault(current, Collections.emptyList());

            for (String neighbor : neighbors) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.offer(neighbor);
                }
            }
        }

        return visited;
    }

    private WorkflowValidationResult validateTerminalEdges(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        if (edges == null || edges.isEmpty()) {
            return result;
        }
        Map<String, WorkflowNode> nodeMap = toNodeMap(nodes);
        for (WorkflowEdge edge : edges) {
            WorkflowNode source = nodeMap.get(edge.getSource());
            if (source != null && source.getType() == NodeType.END) {
                addNodeError(result, "END_HAS_OUTGOING_EDGE", source, "结束节点不能有出边", "删除结束节点后的连线，或把后续处理节点放到 END 之前");
            }
        }
        return result;
    }

    private WorkflowValidationResult validateBranchHandles(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        if (edges == null || edges.isEmpty()) {
            return result;
        }
        Map<String, WorkflowNode> nodeMap = toNodeMap(nodes);
        Map<String, List<WorkflowEdge>> outgoingByNode = groupOutgoing(edges);
        for (WorkflowNode node : nodes) {
            if (node.getType() != NodeType.IF_ELSE && node.getType() != NodeType.QUESTION_CLASSIFIER && node.getType() != NodeType.LOOP) {
                continue;
            }
            Set<String> declaredHandles = declaredHandles(node);
            for (WorkflowEdge edge : outgoingByNode.getOrDefault(node.getId(), Collections.emptyList())) {
                String handle = edge.getSourceHandle();
                if (handle != null && !handle.isBlank() && !declaredHandles.contains(handle)) {
                    addNodeError(result, "BRANCH_HANDLE_UNKNOWN", node,
                            "路由端口 " + handle + " 未在节点配置中声明",
                            "重新连接到已声明分支，或在节点配置中补齐该分支");
                }
                if (!nodeMap.containsKey(edge.getTarget())) {
                    addNodeError(result, "BRANCH_TARGET_UNKNOWN", node,
                            "路由端口 " + safe(edge.getSourceHandle()) + " 指向不存在的节点",
                            "删除无效连线并重新连接目标节点");
                }
            }
        }
        return result;
    }

    private WorkflowValidationResult validateNodeContracts(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        for (WorkflowNode node : nodes) {
            Map<String, Object> data = data(node);
            switch (node.getType()) {
                case LLM -> {
                    if (isBlank(data.get("modelId"))) {
                        addNodeError(result, "AGENT_MODEL_REQUIRED", node, "模型智能体必须选择模型", "在模型智能体配置中选择可用模型");
                    }
                    if (isBlank(data.get("promptTemplate"))) {
                        addNodeWarning(result, "AGENT_PROMPT_EMPTY", node, "模型智能体未配置提示词", "补充清晰任务指令和输入变量引用");
                    }
                }
                case AGENT -> {
                    if (isBlank(data.get("agentId"))) {
                        addNodeError(result, "AGENT_ID_REQUIRED", node, "子智能体必须选择目标智能体", "在子智能体配置中选择已发布智能体");
                    }
                }
                case TOOL -> {
                    if (isBlank(data.get("mcpServerId"))) {
                        addNodeError(result, "TOOL_SERVER_REQUIRED", node, "工具节点必须选择 MCP 服务器", "选择 MCP 服务器后再选择工具");
                    }
                    if (isBlank(data.get("toolName"))) {
                        addNodeError(result, "TOOL_NAME_REQUIRED", node, "工具节点必须选择具体工具", "从 MCP 工具列表中选择要调用的工具");
                    }
                    if (isBlank(data.get("outputVariable"))) {
                        addNodeWarning(result, "TOOL_OUTPUT_VARIABLE_EMPTY", node, "工具节点未配置输出变量", "配置输出变量，便于后续节点引用工具结果");
                    }
                }
                case KNOWLEDGE_RETRIEVAL -> {
                    if (!hasNonEmptyList(data.get("knowledgeBaseIds"))) {
                        addNodeError(result, "KNOWLEDGE_BASE_REQUIRED", node, "检索智能体必须选择知识库", "选择至少一个知识库作为检索来源");
                    }
                    if (isBlank(data.get("queryVariable"))) {
                        addNodeError(result, "KNOWLEDGE_QUERY_REQUIRED", node, "检索智能体必须配置查询变量", "使用 {{inputs.xxx}} 或 {{nodes.xxx.yyy}} 作为查询来源");
                    }
                }
                case HTTP_REQUEST -> {
                    String url = stringValue(data.get("url"));
                    if (url == null || url.isBlank()) {
                        addNodeError(result, "HTTP_URL_REQUIRED", node, "接口调用必须配置 URL", "填写 http 或 https URL");
                    } else if (!url.startsWith("http://") && !url.startsWith("https://")) {
                        addNodeError(result, "HTTP_URL_INVALID", node, "接口调用 URL 仅允许 http/https", "改为 http:// 或 https:// 开头的 URL");
                    }
                }
                case QUESTION_CLASSIFIER -> {
                    if (listSize(data.get("categories")) < 2) {
                        addNodeError(result, "CLASSIFIER_CATEGORIES_REQUIRED", node, "分类路由智能体必须配置分类", "添加至少两个分类并连接对应分支");
                    }
                }
                case PARAMETER_EXTRACTOR -> {
                    if (!hasNonEmptyList(data.get("parameters"))) {
                        addNodeError(result, "EXTRACTOR_PARAMETERS_REQUIRED", node, "结构化提取智能体必须配置参数", "添加需要提取的字段和类型");
                    }
                }
                default -> {
                }
            }
        }
        return result;
    }

    private WorkflowValidationResult validateNodeConfigSchema(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        for (WorkflowNode node : nodes) {
            WorkflowNodeDefinition definition = WorkflowNodeDefinitionCatalog.require(node.getType());
            Map<String, Object> data = data(node);
            for (WorkflowNodeConfigField field : definition.configSchema().fields()) {
                if (!field.required()) {
                    continue;
                }
                Object value = data.get(field.name());
                if (isMissingRequiredValue(value)) {
                    addNodeError(result, "NODE_CONFIG_REQUIRED", node,
                            "节点缺少必填配置 " + field.name() + "（" + field.label() + "）",
                            "请在节点配置面板补充 " + field.label());
                }
            }
        }
        return result;
    }

    private WorkflowValidationResult validateVariableReferences(List<WorkflowNode> nodes) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        Set<String> nodeIds = nodes.stream().map(WorkflowNode::getId).collect(Collectors.toSet());
        Set<String> inputNames = collectInputNames(nodes);
        for (WorkflowNode node : nodes) {
            for (String reference : collectReferences(data(node))) {
                if (reference.startsWith("inputs.")) {
                    String inputName = reference.substring("inputs.".length()).split("\\.", 2)[0];
                    if (!inputNames.contains(inputName)) {
                        addNodeError(result, "VARIABLE_INPUT_UNKNOWN", node,
                                "输入变量 " + reference + " 未在开始节点声明",
                                "在 START 节点添加该输入字段，或改为已存在变量");
                    }
                } else if (reference.startsWith("nodes.")) {
                    String[] parts = reference.split("\\.", 3);
                    if (parts.length < 3 || !nodeIds.contains(parts[1])) {
                        addNodeError(result, "VARIABLE_NODE_UNKNOWN", node,
                                "节点变量 " + reference + " 引用了不存在的节点",
                                "检查节点 ID 和变量引用格式 {{nodes.nodeId.variable}}");
                    }
                } else if (!reference.startsWith("sys.") && !reference.startsWith("env.")) {
                    addNodeWarning(result, "VARIABLE_SCOPE_AMBIGUOUS", node,
                            "变量引用 " + reference + " 缺少作用域前缀",
                            "使用 {{inputs.xxx}}、{{nodes.xxx.yyy}} 或 {{sys.xxx}}");
                }
            }
        }
        return result;
    }

    private WorkflowValidationResult validateRuntimeRisk(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        WorkflowValidationResult result = WorkflowValidationResult.success();
        Map<String, List<WorkflowEdge>> outgoingByNode = groupOutgoing(edges);
        for (WorkflowNode node : nodes) {
            if (node.getType() == NodeType.LOOP) {
                int maxIterations = intValue(data(node).get("maxIterations"), 0);
                if (maxIterations <= 0) {
                    addNodeError(result, "LOOP_MAX_ITERATIONS_REQUIRED", node, "循环路由必须设置大于 0 的最大迭代次数", "设置明确的循环上限");
                } else if (maxIterations > LOOP_WARNING_THRESHOLD) {
                    addNodeWarning(result, "LOOP_MAX_ITERATIONS_HIGH", node,
                            "循环路由最大迭代次数为 " + maxIterations + "，可能造成高成本运行",
                            "降低上限，或增加提前退出条件");
                }
            }
            if (node.getType() == NodeType.PARALLEL) {
                int branchCount = outgoingByNode.getOrDefault(node.getId(), Collections.emptyList()).size();
                if (branchCount < 2) {
                    addNodeError(result, "PARALLEL_BRANCH_TOO_FEW", node, "并行路由至少需要两个分支", "增加分支或改用普通顺序连线");
                } else if (branchCount > PARALLEL_WARNING_THRESHOLD) {
                    addNodeWarning(result, "PARALLEL_BRANCH_TOO_MANY", node,
                            "并行路由包含 " + branchCount + " 个分支，可能造成并发成本峰值",
                            "拆分并行层级，或降低单次并发分支数");
                }
            }
        }
        return result;
    }

    /**
     * 快速检查工作流是否有效（不返回详细错误信息）
     *
     * @param graph 工作流图
     * @return 是否有效
     */
    public boolean isValid(WorkflowGraph graph) {
        return validate(graph).isValid();
    }

    private Map<String, WorkflowNode> toNodeMap(List<WorkflowNode> nodes) {
        return nodes.stream().filter(node -> node.getId() != null)
                .collect(Collectors.toMap(WorkflowNode::getId, node -> node, (left, right) -> left));
    }

    private Map<String, List<WorkflowEdge>> groupOutgoing(List<WorkflowEdge> edges) {
        if (edges == null) {
            return Collections.emptyMap();
        }
        return edges.stream()
                .filter(edge -> edge.getSource() != null)
                .collect(Collectors.groupingBy(WorkflowEdge::getSource));
    }

    private Set<String> declaredHandles(WorkflowNode node) {
        Map<String, Object> data = data(node);
        Set<String> handles = new HashSet<>();
        if (node.getType() == NodeType.LOOP) {
            handles.add("branch:loop");
            handles.add("branch:exit");
            return handles;
        }
        Object branchSource = node.getType() == NodeType.QUESTION_CLASSIFIER ? data.get("categories") : data.get("branches");
        if (branchSource instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map && map.get("id") != null) {
                    String id = String.valueOf(map.get("id"));
                    handles.add("branch:" + id);
                }
            }
        }
        return handles;
    }

    private Set<String> collectInputNames(List<WorkflowNode> nodes) {
        Set<String> inputNames = new HashSet<>();
        for (WorkflowNode node : nodes) {
            if (node.getType() != NodeType.START) {
                continue;
            }
            Object fields = data(node).get("fields");
            if (fields instanceof Collection<?> collection) {
                for (Object field : collection) {
                    if (field instanceof Map<?, ?> map && map.get("name") != null) {
                        inputNames.add(String.valueOf(map.get("name")));
                    }
                }
            }
        }
        return inputNames;
    }

    private List<String> collectReferences(Object value) {
        List<String> refs = new ArrayList<>();
        collectReferences(value, refs);
        return refs;
    }

    @SuppressWarnings("unchecked")
    private void collectReferences(Object value, List<String> refs) {
        if (value instanceof String text) {
            Matcher matcher = VARIABLE_PATTERN.matcher(text);
            while (matcher.find()) {
                refs.add(matcher.group(1).trim());
            }
        } else if (value instanceof Map<?, ?> map) {
            for (Object item : map.values()) {
                collectReferences(item, refs);
            }
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                collectReferences(item, refs);
            }
        }
    }

    private Map<String, Object> data(WorkflowNode node) {
        return node.getData() == null ? Collections.emptyMap() : node.getData();
    }

    private boolean hasNonEmptyList(Object value) {
        return value instanceof Collection<?> collection && !collection.isEmpty();
    }

    private int listSize(Object value) {
        return value instanceof Collection<?> collection ? collection.size() : 0;
    }

    private boolean isBlank(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        return false;
    }

    private boolean isMissingRequiredValue(Object value) {
        if (isBlank(value)) {
            return true;
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void addNodeError(WorkflowValidationResult result, String code, WorkflowNode node, String message, String suggestion) {
        result.addError(code, nodeName(node) + ": " + message, node.getId(), nodeName(node), node.getType(), suggestion);
    }

    private void addNodeWarning(WorkflowValidationResult result, String code, WorkflowNode node, String message, String suggestion) {
        result.addWarning(code, nodeName(node) + ": " + message, node.getId(), nodeName(node), node.getType(), suggestion);
    }

    private String nodeName(WorkflowNode node) {
        return node.getLabel() == null || node.getLabel().isBlank() ? node.getId() : node.getLabel();
    }
}
