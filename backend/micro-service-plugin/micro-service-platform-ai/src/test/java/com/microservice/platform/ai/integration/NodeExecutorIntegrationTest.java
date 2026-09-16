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

package com.microservice.platform.ai.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.agent.impl.*;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.expression.ExpressionEvaluator;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Node Agent Integration Test
 *
 * @author xJh
 * @since 2026/01/07
 */
@DisplayName("Node Agent Integration Test")
class NodeExecutorIntegrationTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final ExpressionEvaluator EXPRESSION_EVALUATOR = new ExpressionEvaluator();
    private static final RestTemplate REST_TEMPLATE = new RestTemplate();

    @Nested
    @DisplayName("LOOP Node Tests")
    class LoopNodeExecutorTests {

        private LoopNodeExecutor loopNodeExecutor;

        @BeforeEach
        void setUp() {
            loopNodeExecutor = new LoopNodeExecutor(EXPRESSION_EVALUATOR);
        }

        @Test
        @DisplayName("LOOP node should return correct type")
        void shouldReturnCorrectType() {
            assertEquals(NodeType.LOOP, loopNodeExecutor.getType());
        }

        @Test
        @DisplayName("LOOP node should continue when under max iterations")
        void shouldContinueWhenUnderMaxIterations() {
            Map<String, Object> data = new HashMap<>();
            data.put("maxIterations", 10);

            WorkflowNode node = createNodeWithData("loop_1", NodeType.LOOP, "Loop", data);
            ExecutionContext context = createTestContext();

            NodeExecutionResult result = loopNodeExecutor.execute(node, context);

            assertTrue(result.isSuccess());
            assertTrue(result.getContinueLoop());
        }

        @Test
        @DisplayName("LOOP node should exit when max iterations reached")
        void shouldExitWhenMaxIterationsReached() {
            Map<String, Object> data = new HashMap<>();
            data.put("maxIterations", 1);

            WorkflowNode node = createNodeWithData("loop_1", NodeType.LOOP, "Loop", data);
            ExecutionContext context = createTestContext();

            // First iteration
            loopNodeExecutor.execute(node, context);
            // Second iteration should exit
            NodeExecutionResult result = loopNodeExecutor.execute(node, context);

            assertTrue(result.isSuccess());
            assertFalse(result.getContinueLoop());
        }
    }

    @Nested
    @DisplayName("CODE Node Tests")
    class CodeNodeExecutorTests {

        private CodeNodeExecutor codeNodeExecutor;

        @BeforeEach
        void setUp() {
            codeNodeExecutor = new CodeNodeExecutor(OBJECT_MAPPER);
        }

        @Test
        @DisplayName("CODE node should return correct type")
        void shouldReturnCorrectType() {
            assertEquals(NodeType.CODE, codeNodeExecutor.getType());
        }

        @Test
        @DisplayName("CODE node should execute simple JavaScript")
        void shouldExecuteSimpleJavaScript() {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "1 + 2");
            data.put("language", "JAVASCRIPT");

            WorkflowNode node = createNodeWithData("code_1", NodeType.CODE, "Code", data);
            ExecutionContext context = createTestContext();

            NodeExecutionResult result = codeNodeExecutor.execute(node, context);

            assertTrue(result.isSuccess());
            assertNotNull(result.getOutputs());
            assertEquals(3L, result.getOutputs().get("result"));
        }

        @Test
        @DisplayName("CODE node should access context variables")
        void shouldAccessContextVariables() {
            Map<String, Object> data = new HashMap<>();
            data.put("code", "x * 2");
            data.put("language", "JAVASCRIPT");

            WorkflowNode node = createNodeWithData("code_1", NodeType.CODE, "Code", data);
            ExecutionContext context = createTestContext();
            context.setVariable("x", 5);

            NodeExecutionResult result = codeNodeExecutor.execute(node, context);

            assertTrue(result.isSuccess());
            assertEquals(10L, result.getOutputs().get("result"));
        }
    }

    @Nested
    @DisplayName("HTTP Node Tests")
    class HttpNodeExecutorTests {

        private HttpNodeExecutor httpNodeExecutor;

        @BeforeEach
        void setUp() {
            httpNodeExecutor = new HttpNodeExecutor(OBJECT_MAPPER);
        }

        @Test
        @DisplayName("HTTP_REQUEST node should return correct type")
        void shouldReturnCorrectType() {
            assertEquals(NodeType.HTTP_REQUEST, httpNodeExecutor.getType());
        }

        @Test
        @DisplayName("HTTP_REQUEST node should validate URL is required")
        void shouldValidateUrlIsRequired() {
            Map<String, Object> data = new HashMap<>();
            // No URL configured

            WorkflowNode node = createNodeWithData("http_1", NodeType.HTTP_REQUEST, "HTTP", data);

            assertThrows(IllegalArgumentException.class, () -> httpNodeExecutor.validate(node));
        }

        @Test
        @DisplayName("HTTP_REQUEST node should pass validation with URL")
        void shouldPassValidationWithUrl() {
            Map<String, Object> data = new HashMap<>();
            data.put("url", "https://api.example.com/test");

            WorkflowNode node = createNodeWithData("http_1", NodeType.HTTP_REQUEST, "HTTP", data);

            assertDoesNotThrow(() -> httpNodeExecutor.validate(node));
        }
    }

    // ==================== Helper Methods ====================

    private static WorkflowNode createNode(String id, NodeType type, String label) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(label)
                .position(WorkflowNode.Position.builder().x(100.0).y(100.0).build())
                .data(new HashMap<>())
                .build();
    }

    private static WorkflowNode createNodeWithData(String id, NodeType type, String label, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(label)
                .position(WorkflowNode.Position.builder().x(100.0).y(100.0).build())
                .data(new HashMap<>(data))
                .build();
    }

    private static ExecutionContext createTestContext() {
        return ExecutionContext.builder()
                .executionId(UUID.randomUUID().toString())
                .workflowId(1L)
                .workflowVersion(1)
                .userId(1L)
                .tenantId(1L)
                .build();
    }
}
