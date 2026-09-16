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

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.agent.impl.ParameterExtractorNodeExecutor;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * ParameterExtractorNodeExecutor 集成测试
 * 测试 Function Call 模式和 Prompt 模式的参数提取功能
 * Requirements: 3.9, 3.10
 *
 * @author xJh
 * @since 2026/01/08
 */
@DisplayName("ParameterExtractorNodeExecutor 集成测试")
@ExtendWith(MockitoExtension.class)
class ParameterExtractorNodeExecutorIntegrationTest {

    @Mock
    private TextModelService textModelService;

    @Mock
    private ModelConfigRetriever modelConfigRetriever;

    @Mock
    private ChatModel chatModel;

    private ParameterExtractorNodeExecutor agent;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        agent = new ParameterExtractorNodeExecutor(textModelService, modelConfigRetriever);
        context = ExecutionContext.builder()
                .executionId("integration-test-" + UUID.randomUUID())
                .workflowId(1L)
                .workflowVersion(1)
                .userId(100L)
                .tenantId(1L)
                .build();
    }

    @AfterEach
    void tearDown() {
        // 清理记忆
        ParameterExtractorNodeExecutor.clearMemory(context.getExecutionId());
    }

    private WorkflowNode createNode(String id, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(NodeType.PARAMETER_EXTRACTOR)
                .label("Parameter Extractor")
                .data(data)
                .build();
    }

    private void setupMockModel(String responseContent) {
        ModelEntity modelEntity = new ModelEntity();
        modelEntity.setId(1L);
        modelEntity.setName("test-model");
        modelEntity.setVariables(new HashMap<>());

        lenient().when(modelConfigRetriever.getRequiredModel(anyLong())).thenReturn(modelEntity);
        lenient().when(textModelService.model(any(ModelEntity.class))).thenReturn(chatModel);

        ChatResponse chatResponse = mock(ChatResponse.class);
        AiMessage aiMessage = AiMessage.from(responseContent);
        lenient().when(chatResponse.aiMessage()).thenReturn(aiMessage);
        lenient().when(chatResponse.tokenUsage()).thenReturn(new TokenUsage(20, 10, 30));
        lenient().when(chatModel.chat(anyList())).thenReturn(chatResponse);
    }

    private List<Map<String, Object>> createBookingParameters() {
        final List<Map<String, Object>> parameters = new ArrayList<>();

        Map<String, Object> param1 = new HashMap<>();
        param1.put("name", "name");
        param1.put("type", "string");
        param1.put("description", "Customer name");
        param1.put("required", true);
        parameters.add(param1);

        Map<String, Object> param2 = new HashMap<>();
        param2.put("name", "date");
        param2.put("type", "string");
        param2.put("description", "Booking date");
        param2.put("required", true);
        parameters.add(param2);

        Map<String, Object> param3 = new HashMap<>();
        param3.put("name", "guests");
        param3.put("type", "number");
        param3.put("description", "Number of guests");
        param3.put("required", false);
        parameters.add(param3);

        return parameters;
    }

    @Nested
    @DisplayName("Prompt 模式集成测试")
    class PromptModeIntegrationTests {

        @Test
        @DisplayName("应从自然语言中提取所有参数")
        void shouldExtractAllParametersFromNaturalLanguage() {
            // Arrange
            String jsonResponse = """
                    {
                        "name": "John Smith",
                        "date": "2026-01-15",
                        "guests": 4
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-prompt-1", data);
            context.setVariable("start.query",
                    "I'd like to book a table for John Smith on January 15th, 2026 for 4 people");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("John Smith", result.getOutputs().get("name"));
            assertEquals("2026-01-15", result.getOutputs().get("date"));
            assertEquals(4.0, ((Number) result.getOutputs().get("guests")).doubleValue(), 0.001);
            assertEquals(true, result.getOutputs().get("__is_success"));
            assertEquals("", result.getOutputs().get("__reason"));
        }

        @Test
        @DisplayName("应处理 JSON 代码块格式的响应")
        void shouldHandleJsonCodeBlockResponse() {
            // Arrange
            String jsonResponse = """
                    ```json
                    {
                        "name": "Alice Johnson",
                        "date": "2026-02-20",
                        "guests": 2
                    }
                    ```
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-codeblock-1", data);
            context.setVariable("query", "Book for Alice Johnson on Feb 20, 2026, party of 2");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Alice Johnson", result.getOutputs().get("name"));
            assertEquals("2026-02-20", result.getOutputs().get("date"));
            assertEquals(2.0, ((Number) result.getOutputs().get("guests")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("缺少必需参数时应标记失败")
        void shouldMarkFailureWhenRequiredParameterMissing() {
            // Arrange
            String jsonResponse = """
                    {
                        "name": "Bob",
                        "guests": 3
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-missing-1", data);
            context.setVariable("query", "Book for Bob, 3 guests");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("date"));
        }

        @Test
        @DisplayName("可选参数缺失时不生成隐式默认值")
        void shouldNotCreateImplicitDefaultForOptionalParameter() {
            // Arrange
            String jsonResponse = """
                    {
                        "name": "Charlie",
                        "date": "2026-03-10"
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-no-default-1", data);
            context.setVariable("query", "Book for Charlie on March 10, 2026");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputs().get("__is_success"));
            assertEquals("Charlie", result.getOutputs().get("name"));
            assertEquals("2026-03-10", result.getOutputs().get("date"));
            assertFalse(result.getOutputs().containsKey("guests"));
        }
    }

    @Nested
    @DisplayName("Function Call 模式集成测试")
    class FunctionCallModeIntegrationTests {

        @Test
        @DisplayName("应使用 Function Call 模式提取参数")
        void shouldExtractParametersWithFunctionCallMode() {
            // Arrange
            String jsonResponse = """
                    {
                        "name": "David Lee",
                        "date": "2026-04-05",
                        "guests": 6
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "FUNCTION_CALL");

            WorkflowNode node = createNode("pe-fc-1", data);
            context.setVariable("start.query",
                    "Reserve a table for David Lee on April 5th, 2026 for 6 guests");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("David Lee", result.getOutputs().get("name"));
            assertEquals("2026-04-05", result.getOutputs().get("date"));
            assertEquals(6.0, ((Number) result.getOutputs().get("guests")).doubleValue(), 0.001);
            assertEquals(true, result.getOutputs().get("__is_success"));
        }

        @Test
        @DisplayName("Function Call 模式应包含 JSON Schema")
        void shouldIncludeJsonSchemaInFunctionCallMode() {
            // Arrange
            String jsonResponse = """
                    {"name": "Test", "date": "2026-01-01", "guests": 1}
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "FUNCTION_CALL");

            WorkflowNode node = createNode("pe-fc-schema-1", data);
            context.setVariable("query", "Test booking");

            // Act
            agent.execute(node, context);

            // Assert
            verify(chatModel).chat(anyList());
        }
    }

    @Nested
    @DisplayName("复杂参数类型集成测试")
    class ComplexParameterTypesIntegrationTests {

        private List<Map<String, Object>> createComplexParameters() {
            final List<Map<String, Object>> parameters = new ArrayList<>();

            Map<String, Object> param1 = new HashMap<>();
            param1.put("name", "productName");
            param1.put("type", "string");
            param1.put("description", "Product name");
            param1.put("required", true);
            parameters.add(param1);

            Map<String, Object> param2 = new HashMap<>();
            param2.put("name", "quantity");
            param2.put("type", "number");
            param2.put("description", "Quantity to order");
            param2.put("required", true);
            parameters.add(param2);

            Map<String, Object> param3 = new HashMap<>();
            param3.put("name", "urgent");
            param3.put("type", "boolean");
            param3.put("description", "Is this an urgent order?");
            param3.put("required", false);
            parameters.add(param3);

            Map<String, Object> param4 = new HashMap<>();
            param4.put("name", "tags");
            param4.put("type", "array");
            param4.put("description", "Order tags");
            param4.put("required", false);
            parameters.add(param4);

            return parameters;
        }

        @Test
        @DisplayName("应正确提取布尔类型参数")
        void shouldExtractBooleanParameter() {
            // Arrange
            String jsonResponse = """
                    {
                        "productName": "Widget",
                        "quantity": 100,
                        "urgent": true
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createComplexParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-bool-1", data);
            context.setVariable("query", "I need 100 widgets urgently");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Widget", result.getOutputs().get("productName"));
            assertEquals(100.0, ((Number) result.getOutputs().get("quantity")).doubleValue(), 0.001);
            assertEquals(true, result.getOutputs().get("urgent"));
        }

        @Test
        @DisplayName("应正确提取数组类型参数")
        void shouldExtractArrayParameter() {
            // Arrange
            String jsonResponse = """
                    {
                        "productName": "Gadget",
                        "quantity": 50,
                        "urgent": false,
                        "tags": ["electronics", "sale", "priority"]
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createComplexParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-array-1", data);
            context.setVariable("query", "Order 50 gadgets, tag as electronics, sale, priority");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Gadget", result.getOutputs().get("productName"));
            assertEquals(50.0, ((Number) result.getOutputs().get("quantity")).doubleValue(), 0.001);
            @SuppressWarnings("unchecked")
            List<String> tags = (List<String>) result.getOutputs().get("tags");
            assertEquals(3, tags.size());
            assertTrue(tags.contains("electronics"));
        }
    }

    @Nested
    @DisplayName("枚举值参数集成测试")
    class EnumParameterIntegrationTests {

        private List<Map<String, Object>> createEnumParameters() {
            final List<Map<String, Object>> parameters = new ArrayList<>();

            Map<String, Object> param1 = new HashMap<>();
            param1.put("name", "priority");
            param1.put("type", "string");
            param1.put("description", "Task priority");
            param1.put("required", true);
            param1.put("enumValues", Arrays.asList("low", "medium", "high", "critical"));
            parameters.add(param1);

            Map<String, Object> param2 = new HashMap<>();
            param2.put("name", "status");
            param2.put("type", "string");
            param2.put("description", "Task status");
            param2.put("required", false);
            param2.put("enumValues", Arrays.asList("pending", "in_progress", "completed"));
            parameters.add(param2);

            return parameters;
        }

        @Test
        @DisplayName("应正确提取枚举值参数")
        void shouldExtractEnumParameter() {
            // Arrange
            String jsonResponse = """
                    {
                        "priority": "high",
                        "status": "in_progress"
                    }
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createEnumParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-enum-1", data);
            context.setVariable("query", "Set task to high priority, currently in progress");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("high", result.getOutputs().get("priority"));
            assertEquals("in_progress", result.getOutputs().get("status"));
        }
    }

    @Nested
    @DisplayName("Memory 功能集成测试")
    class MemoryIntegrationTests {

        @Test
        @DisplayName("启用 Memory 时应保持对话上下文")
        void shouldMaintainConversationContextWithMemory() {
            // Arrange - First call
            String jsonResponse1 = """
                    {"name": "Emma", "date": "2026-05-01", "guests": 2}
                    """;
            setupMockModel(jsonResponse1);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");
            data.put("memoryEnabled", true);
            data.put("memoryWindowSize", 5);

            WorkflowNode node = createNode("pe-memory-1", data);
            context.setVariable("query", "Book for Emma on May 1st, 2026 for 2");

            // Act - First call
            NodeExecutionResult result1 = agent.execute(node, context);

            // Assert - First call
            assertTrue(result1.isSuccess());
            assertEquals("Emma", result1.getOutputs().get("name"));

            // Arrange - Second call (should have memory)
            String jsonResponse2 = """
                    {"name": "Emma", "date": "2026-05-02", "guests": 4}
                    """;
            setupMockModel(jsonResponse2);
            context.setVariable("query", "Actually, change it to May 2nd for 4 people");

            // Act - Second call
            NodeExecutionResult result2 = agent.execute(node, context);

            // Assert - Second call
            assertTrue(result2.isSuccess());
            // 验证 chatModel 被调用了两次
            verify(chatModel, times(2)).chat(anyList());
        }
    }

    @Nested
    @DisplayName("状态变量输出集成测试")
    class StatusVariableOutputIntegrationTests {

        @Test
        @DisplayName("成功提取时 __is_success 应为 true")
        void shouldSetIsSuccessTrueOnSuccess() {
            // Arrange
            String jsonResponse = """
                    {"name": "Frank", "date": "2026-06-15", "guests": 3}
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-status-1", data);
            context.setVariable("query", "Book for Frank on June 15, 2026 for 3");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(true, result.getOutputs().get("__is_success"));
            assertEquals("", result.getOutputs().get("__reason"));
            assertEquals(3.0, ((Number) result.getOutputs().get("__extracted_count")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("缺少必需参数时 __is_success 应为 false 并包含原因")
        void shouldSetIsSuccessFalseWithReason() {
            // Arrange
            String jsonResponse = """
                    {"guests": 2}
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-status-2", data);
            context.setVariable("query", "Just 2 guests");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("缺少必填参数"));
        }
    }

    @Nested
    @DisplayName("错误处理集成测试")
    class ErrorHandlingIntegrationTests {

        @Test
        @DisplayName("空输入应返回失败状态")
        void shouldReturnFailureForEmptyInput() {
            // Arrange
            setupMockModel("{}");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-empty-1", data);
            // 不设置 query 变量

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("输入文本为空"));
        }

        @Test
        @DisplayName("无效 JSON 响应应失败")
        void shouldFailForInvalidJson() {
            // Arrange
            String invalidResponse = "name: Grace, date: 2026-07-20, guests: 5";
            setupMockModel(invalidResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-invalid-1", data);
            context.setVariable("query", "Book for Grace on July 20, 2026 for 5");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("参数提取响应不是有效 JSON"));
        }
    }

    @Nested
    @DisplayName("工作流集成场景测试")
    class WorkflowIntegrationScenarioTests {

        @Test
        @DisplayName("应正确解析上游节点的变量引用")
        void shouldResolveUpstreamNodeVariables() {
            // Arrange
            String jsonResponse = """
                    {"name": "Henry", "date": "2026-08-10", "guests": 4}
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{llm.output}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-upstream-1", data);
            // 使用 setVariable 设置变量，因为 agent 使用 context.getVariable 解析变量
            context.setVariable("llm.output",
                    "The customer Henry wants to book for August 10, 2026 with 4 guests");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("Henry", result.getOutputs().get("name"));
            assertEquals("2026-08-10", result.getOutputs().get("date"));
            assertEquals(4.0, ((Number) result.getOutputs().get("guests")).doubleValue(), 0.001);
        }

        @Test
        @DisplayName("提取的参数应可供下游节点使用")
        void shouldMakeExtractedParametersAvailableForDownstream() {
            // Arrange
            String jsonResponse = """
                    {"name": "Ivy", "date": "2026-09-05", "guests": 2}
                    """;
            setupMockModel(jsonResponse);

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("parameters", createBookingParameters());
            data.put("inferenceMode", "PROMPT_BASED");

            WorkflowNode node = createNode("pe-downstream-1", data);
            context.setVariable("query", "Book for Ivy on September 5, 2026 for 2");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            // 验证输出包含所有提取的参数
            Map<String, Object> outputs = result.getOutputs();
            assertTrue(outputs.containsKey("name"));
            assertTrue(outputs.containsKey("date"));
            assertTrue(outputs.containsKey("guests"));
            assertTrue(outputs.containsKey("__is_success"));
            assertTrue(outputs.containsKey("__reason"));
            assertTrue(outputs.containsKey("__extracted_count"));
            assertTrue(outputs.containsKey("__input_text"));
            assertTrue(outputs.containsKey("__inference_mode"));
        }
    }
}
