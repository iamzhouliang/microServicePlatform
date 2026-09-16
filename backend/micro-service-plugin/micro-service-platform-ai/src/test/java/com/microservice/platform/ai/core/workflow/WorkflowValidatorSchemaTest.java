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
import com.microservice.platform.ai.domain.entity.workflow.WorkflowEdge;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowValidatorSchemaTest {

    @Test
    void shouldReportMissingRequiredNodeConfigFromSchema() {
        WorkflowGraph graph = new WorkflowGraph();
        graph.setNodes(List.of(
                node("start", NodeType.START, Map.of("fields", List.of(Map.of("name", "query")))),
                node("llm", NodeType.LLM, Map.of("outputVariable", "result")),
                node("end", NodeType.END, Map.of("outputs", List.of()))));
        graph.setEdges(List.of(
                edge("e1", "start", "llm"),
                edge("e2", "llm", "end")));

        WorkflowValidationResult result = new WorkflowValidator().validate(graph);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues())
                .anyMatch(issue -> "NODE_CONFIG_REQUIRED".equals(issue.getCode())
                        && "llm".equals(issue.getNodeId())
                        && issue.getMessage().contains("modelId"));
    }

    private static WorkflowNode node(String id, NodeType type, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(id)
                .data(data)
                .build();
    }

    private static WorkflowEdge edge(String id, String source, String target) {
        return WorkflowEdge.builder()
                .id(id)
                .source(source)
                .target(target)
                .build();
    }
}
