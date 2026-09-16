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

import java.util.List;
import java.util.Map;

/**
 * 编译后的 LangChain4j 智能体工作流元数据。
 *
 * @param executionOrder executionOrder 参数
 * @param nodes 节点集合
 * @param outgoingEdges outgoingEdges 参数
 * @param startNode startNode 参数
 * @param terminalNodes terminalNodes 参数
 * @param workflowModel workflowModel 参数
 * @author xJh
 * @since 2026/05/24
 */
public record CompiledWorkflow(
        CompiledNode startNode,
        List<CompiledNode> terminalNodes,
        List<CompiledNode> executionOrder,
        Map<String, CompiledNode> nodes,
        Map<String, List<CompiledEdge>> outgoingEdges,
        LangChain4jWorkflowFactory.WorkflowModel workflowModel) {

    public CompiledNode node(String nodeId) {
        CompiledNode node = nodes.get(nodeId);
        if (node == null) {
            throw new IllegalArgumentException("未知的已编译节点: " + nodeId);
        }
        return node;
    }

    public CompiledWorkflow withWorkflowModel(LangChain4jWorkflowFactory.WorkflowModel workflowModel) {
        return new CompiledWorkflow(startNode, terminalNodes, executionOrder, nodes, outgoingEdges, workflowModel);
    }

    public record CompiledNode(String id, NodeType type, String displayName, Map<String, Object> config) {

        public String scopeKey(String outputKey) {
            return "nodes." + id + "." + outputKey;
        }
    }

    public record CompiledEdge(String id, String source, String sourceHandle, String target, String targetHandle) {
    }
}
