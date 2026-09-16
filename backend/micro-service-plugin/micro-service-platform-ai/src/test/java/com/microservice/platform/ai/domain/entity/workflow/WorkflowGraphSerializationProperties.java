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

package com.microservice.platform.ai.domain.entity.workflow;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.NodeType;
import net.jqwik.api.*;

import java.util.*;

/**
 * Property-Based Tests for WorkflowGraph Serialization
 * Feature: ai-workflow-orchestration, Property 1: Workflow Graph Serialization Round-Trip
 * Validates: Requirements 1.7, 1.8
 * For any valid WorkflowGraph object, serializing it to JSON and then deserializing back
 * SHALL produce an equivalent WorkflowGraph with identical nodes, edges, and configurations.
 *
 * @author xJh
 * @since 2026/01/07
 */
class WorkflowGraphSerializationProperties {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Property 1: Workflow Graph Serialization Round-Trip
     * For any valid WorkflowGraph, serializing to JSON and deserializing back
     * should produce an equivalent object.
     * Validates: Requirements 1.7, 1.8
     * @param original original 参数
     * @throws JsonProcessingException 处理失败时抛出
     */
    @Property(tries = 100)
    @Label("Property 1: Workflow Graph Serialization Round-Trip")
    void workflowGraphSerializationRoundTrip(
                                             @ForAll("validWorkflowGraphs") WorkflowGraph original) throws JsonProcessingException {

        // Serialize to JSON
        String json = OBJECT_MAPPER.writeValueAsString(original);

        // Deserialize back
        WorkflowGraph deserialized = OBJECT_MAPPER.readValue(json, WorkflowGraph.class);

        // Verify equivalence
        assertWorkflowGraphsEqual(original, deserialized);
    }

    /**
     * Arbitrary provider for valid WorkflowGraph objects
     * @return 处理结果
     */
    @Provide
    Arbitrary<WorkflowGraph> validWorkflowGraphs() {
        return Combinators.combine(
                nodeListArbitrary(),
                edgeListArbitrary()).as(
                        (nodes, edges) -> WorkflowGraph.builder()
                                .nodes(nodes)
                                .edges(edges)
                                .build());
    }

    /**
     * Arbitrary provider for list of WorkflowNodes
     * @return 处理结果
     */
    private Arbitrary<List<WorkflowNode>> nodeListArbitrary() {
        return workflowNodeArbitrary()
                .list()
                .ofMinSize(0)
                .ofMaxSize(10);
    }

    /**
     * Arbitrary provider for a single WorkflowNode
     * @return 处理结果
     */
    private Arbitrary<WorkflowNode> workflowNodeArbitrary() {
        return Combinators.combine(
                nodeIdArbitrary(),
                nodeTypeArbitrary(),
                nodeLabelArbitrary(),
                positionArbitrary(),
                nodeDataArbitrary()).as(
                        (id, type, label, position, data) -> WorkflowNode.builder()
                                .id(id)
                                .type(type)
                                .label(label)
                                .position(position)
                                .data(data)
                                .build());
    }

    /**
     * Arbitrary provider for node ID
     * @return 处理结果
     */
    private Arbitrary<String> nodeIdArbitrary() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> "node_" + s);
    }

    /**
     * Arbitrary provider for node type using NodeType enum
     * @return 处理结果
     */
    private Arbitrary<NodeType> nodeTypeArbitrary() {
        return Arbitraries.of(NodeType.values());
    }

    /**
     * Arbitrary provider for node label
     * @return 处理结果
     */
    private Arbitrary<String> nodeLabelArbitrary() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .withChars(' ', '-', '_')
                .ofMinLength(1)
                .ofMaxLength(50);
    }

    /**
     * Arbitrary provider for Position
     * @return 处理结果
     */
    private Arbitrary<WorkflowNode.Position> positionArbitrary() {
        return Combinators.combine(
                Arbitraries.doubles().between(0.0, 2000.0),
                Arbitraries.doubles().between(0.0, 2000.0)).as(
                        (x, y) -> WorkflowNode.Position.builder()
                                .x(x)
                                .y(y)
                                .build());
    }

    /**
     * Arbitrary provider for node data ({@code Map<String, Object>})
     * Generates simple JSON-serializable data
     * @return 处理结果
     */
    private Arbitrary<Map<String, Object>> nodeDataArbitrary() {
        return Arbitraries.maps(
                Arbitraries.strings().alpha().ofMinLength(1).ofMaxLength(20),
                simpleValueArbitrary()).ofMinSize(0).ofMaxSize(5);
    }

    /**
     * Arbitrary provider for simple JSON-serializable values
     * @return 处理结果
     */
    private Arbitrary<Object> simpleValueArbitrary() {
        return Arbitraries.oneOf(
                Arbitraries.strings().alpha().numeric().ofMinLength(0).ofMaxLength(50).map(s -> (Object) s),
                Arbitraries.integers().between(-1000, 1000).map(i -> (Object) i),
                Arbitraries.doubles().between(-1000.0, 1000.0).map(d -> (Object) d),
                Arbitraries.of(true, false).map(b -> (Object) b));
    }

    /**
     * Arbitrary provider for list of WorkflowEdges
     * @return 处理结果
     */
    private Arbitrary<List<WorkflowEdge>> edgeListArbitrary() {
        return workflowEdgeArbitrary()
                .list()
                .ofMinSize(0)
                .ofMaxSize(15);
    }

    /**
     * Arbitrary provider for a single WorkflowEdge
     * @return 处理结果
     */
    private Arbitrary<WorkflowEdge> workflowEdgeArbitrary() {
        return Combinators.combine(
                edgeIdArbitrary(),
                nodeIdArbitrary(),
                portIdArbitrary(),
                nodeIdArbitrary(),
                portIdArbitrary()).as(
                        (id, source, sourceHandle, target, targetHandle) -> WorkflowEdge.builder()
                                .id(id)
                                .source(source)
                                .sourceHandle(sourceHandle)
                                .target(target)
                                .targetHandle(targetHandle)
                                .build());
    }

    /**
     * Arbitrary provider for edge ID
     * @return 处理结果
     */
    private Arbitrary<String> edgeIdArbitrary() {
        return Arbitraries.strings()
                .alpha()
                .numeric()
                .ofMinLength(1)
                .ofMaxLength(20)
                .map(s -> "edge_" + s);
    }

    /**
     * Arbitrary provider for port ID
     * @return 处理结果
     */
    private Arbitrary<String> portIdArbitrary() {
        return Arbitraries.of("input", "output", "branch:true", "branch:false", "branch:loop", "branch:exit");
    }

    /**
     * Assert that two WorkflowGraph objects are equal
     * @param actual actual 参数
     * @param expected expected 参数
     */
    private void assertWorkflowGraphsEqual(WorkflowGraph expected, WorkflowGraph actual) {
        // Check nodes
        if (expected.getNodes() == null) {
            assert actual.getNodes() == null : "Expected null nodes but got: " + actual.getNodes();
        } else {
            assert actual.getNodes() != null : "Expected non-null nodes";
            assert expected.getNodes().size() == actual.getNodes().size() : "Node count mismatch: expected " + expected.getNodes().size() + " but got " + actual.getNodes().size();

            for (int i = 0; i < expected.getNodes().size(); i++) {
                assertNodesEqual(expected.getNodes().get(i), actual.getNodes().get(i), i);
            }
        }

        // Check edges
        if (expected.getEdges() == null) {
            assert actual.getEdges() == null : "Expected null edges but got: " + actual.getEdges();
        } else {
            assert actual.getEdges() != null : "Expected non-null edges";
            assert expected.getEdges().size() == actual.getEdges().size() : "Edge count mismatch: expected " + expected.getEdges().size() + " but got " + actual.getEdges().size();

            for (int i = 0; i < expected.getEdges().size(); i++) {
                assertEdgesEqual(expected.getEdges().get(i), actual.getEdges().get(i), i);
            }
        }
    }

    /**
     * Assert that two WorkflowNode objects are equal
     * @param actual actual 参数
     * @param expected expected 参数
     * @param index 索引位置
     */
    private void assertNodesEqual(WorkflowNode expected, WorkflowNode actual, int index) {
        assert Objects.equals(expected.getId(), actual.getId()) : "Node[" + index + "] id mismatch: expected '" + expected.getId() + "' but got '" + actual.getId() + "'";
        assert Objects.equals(expected.getType(), actual.getType()) : "Node[" + index + "] type mismatch: expected '" + expected.getType() + "' but got '" + actual.getType() + "'";
        assert Objects.equals(expected.getLabel(), actual.getLabel()) : "Node[" + index + "] label mismatch: expected '" + expected.getLabel() + "' but got '" + actual.getLabel() + "'";

        // Check position
        if (expected.getPosition() == null) {
            assert actual.getPosition() == null : "Node[" + index + "] expected null position";
        } else {
            assert actual.getPosition() != null : "Node[" + index + "] expected non-null position";
            assert Objects.equals(expected.getPosition().getX(), actual.getPosition().getX()) : "Node[" + index + "] position.x mismatch";
            assert Objects.equals(expected.getPosition().getY(), actual.getPosition().getY()) : "Node[" + index + "] position.y mismatch";
        }

        // Check data map
        if (expected.getData() == null) {
            assert actual.getData() == null : "Node[" + index + "] expected null data";
        } else {
            assert actual.getData() != null : "Node[" + index + "] expected non-null data";
            assert expected.getData().size() == actual.getData().size() : "Node[" + index + "] data size mismatch";

            for (Map.Entry<String, Object> entry : expected.getData().entrySet()) {
                Object actualValue = actual.getData().get(entry.getKey());
                // Handle numeric type coercion (Jackson may deserialize integers as different types)
                if (entry.getValue() instanceof Number && actualValue instanceof Number) {
                    assert ((Number) entry.getValue()).doubleValue() == ((Number) actualValue).doubleValue() : "Node[" + index + "] data[" + entry.getKey() + "] value mismatch";
                } else {
                    assert Objects.equals(entry.getValue(), actualValue) : "Node[" + index + "] data[" + entry.getKey() + "] mismatch: expected '"
                            + entry.getValue() + "' but got '" + actualValue + "'";
                }
            }
        }
    }

    /**
     * Assert that two WorkflowEdge objects are equal
     * @param actual actual 参数
     * @param expected expected 参数
     * @param index 索引位置
     */
    private void assertEdgesEqual(WorkflowEdge expected, WorkflowEdge actual, int index) {
        assert Objects.equals(expected.getId(), actual.getId()) : "Edge[" + index + "] id mismatch: expected '" + expected.getId() + "' but got '" + actual.getId() + "'";
        assert Objects.equals(expected.getSource(), actual.getSource()) : "Edge[" + index + "] source mismatch: expected '" + expected.getSource() + "' but got '" + actual.getSource() + "'";
        assert Objects.equals(expected.getSourceHandle(), actual.getSourceHandle())
                : "Edge[" + index + "] sourceHandle mismatch: expected '" + expected.getSourceHandle() + "' but got '" + actual.getSourceHandle() + "'";
        assert Objects.equals(expected.getTarget(), actual.getTarget()) : "Edge[" + index + "] target mismatch: expected '" + expected.getTarget() + "' but got '" + actual.getTarget() + "'";
        assert Objects.equals(expected.getTargetHandle(), actual.getTargetHandle())
                : "Edge[" + index + "] targetHandle mismatch: expected '" + expected.getTargetHandle() + "' but got '" + actual.getTargetHandle() + "'";
    }
}
