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
import com.microservice.platform.ai.core.workflow.agent.impl.QuestionClassifierNodeExecutor;
import com.microservice.platform.ai.core.workflow.config.node.QuestionClassifierConfig;
import com.microservice.platform.ai.core.workflow.config.node.QuestionClassifierConfig.ClassCategory;
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
 * QuestionClassifierNodeExecutor 集成测试
 * 测试 LLM 分类功能和输出端口路由
 * Requirements: 3.7, 3.8
 *
 * @author xJh
 * @since 2026/01/08
 */
@DisplayName("QuestionClassifierNodeExecutor 集成测试")
@ExtendWith(MockitoExtension.class)
class QuestionClassifierNodeExecutorIntegrationTest {

    @Mock
    private TextModelService textModelService;

    @Mock
    private ModelConfigRetriever modelConfigRetriever;

    @Mock
    private ChatModel chatModel;

    private QuestionClassifierNodeExecutor agent;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        agent = new QuestionClassifierNodeExecutor(textModelService, modelConfigRetriever);
        context = ExecutionContext.builder()
                .executionId("integration-test-" + UUID.randomUUID())
                .workflowId(1L)
                .workflowVersion(1)
                .userId(100L)
                .tenantId(1L)
                .build();
    }

    private WorkflowNode createNode(String id, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(NodeType.QUESTION_CLASSIFIER)
                .label("Question Classifier")
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
        lenient().when(chatResponse.tokenUsage()).thenReturn(new TokenUsage(10, 5, 15));
        lenient().when(chatModel.chat(anyList())).thenReturn(chatResponse);
    }

    private List<Map<String, Object>> createTechSupportCategories() {
        final List<Map<String, Object>> categories = new ArrayList<>();

        Map<String, Object> cat1 = new HashMap<>();
        cat1.put("id", "technical");
        cat1.put("name", "Technical Support");
        cat1.put("description", "Questions about technical issues, bugs, and software problems");
        cat1.put("examples", Arrays.asList("My app crashed", "How to fix error 500?"));
        categories.add(cat1);

        Map<String, Object> cat2 = new HashMap<>();
        cat2.put("id", "billing");
        cat2.put("name", "Billing");
        cat2.put("description", "Questions about payments, invoices, and subscriptions");
        cat2.put("examples", Arrays.asList("How to pay?", "Where is my invoice?"));
        categories.add(cat2);

        Map<String, Object> cat3 = new HashMap<>();
        cat3.put("id", "general");
        cat3.put("name", "General Inquiry");
        cat3.put("description", "General questions and other inquiries");
        categories.add(cat3);

        return categories;
    }

    @Nested
    @DisplayName("LLM 分类功能集成测试")
    class LLMClassificationIntegrationTests {

        @Test
        @DisplayName("应正确分类技术支持问题")
        void shouldClassifyTechnicalSupportQuestion() {
            // Arrange
            setupMockModel("technical");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());
            data.put("instructions", "Classify customer support questions accurately.");

            WorkflowNode node = createNode("qc-tech-1", data);
            context.setVariable("start.query", "My application keeps crashing when I try to upload files");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("technical", result.getOutputs().get("category"));
            assertEquals("Technical Support", result.getOutputs().get("categoryName"));
            assertEquals("technical", result.getNextBranch());
            verify(chatModel).chat(anyList());
        }

        @Test
        @DisplayName("应正确分类账单问题")
        void shouldClassifyBillingQuestion() {
            // Arrange
            setupMockModel("billing");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-billing-1", data);
            context.setVariable("start.query", "I need a copy of my invoice from last month");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("billing", result.getOutputs().get("category"));
            assertEquals("Billing", result.getOutputs().get("categoryName"));
            assertEquals("billing", result.getNextBranch());
        }

        @Test
        @DisplayName("应正确分类一般咨询问题")
        void shouldClassifyGeneralInquiry() {
            // Arrange
            setupMockModel("general");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-general-1", data);
            context.setVariable("start.query", "What are your business hours?");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("general", result.getOutputs().get("category"));
            assertEquals("General Inquiry", result.getOutputs().get("categoryName"));
            assertEquals("general", result.getNextBranch());
        }

        @Test
        @DisplayName("应处理 LLM 返回类别名称而非 ID 的情况")
        void shouldHandleCategoryNameResponse() {
            // Arrange
            setupMockModel("Technical Support");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-name-1", data);
            context.setVariable("start.query", "Error 404 on my dashboard");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("technical", result.getOutputs().get("category"));
        }

        @Test
        @DisplayName("应处理 LLM 返回带前缀的响应")
        void shouldHandlePrefixedResponse() {
            // Arrange
            setupMockModel("Category: billing");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-prefix-1", data);
            context.setVariable("start.query", "How do I update my payment method?");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("billing", result.getOutputs().get("category"));
        }
    }

    @Nested
    @DisplayName("输出端口路由集成测试")
    class OutputPortRoutingIntegrationTests {

        @Test
        @DisplayName("应为每个分类类别生成正确的输出端口")
        void shouldRouteToCorrectOutputPort() {
            // Arrange
            setupMockModel("technical");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-route-1", data);
            context.setVariable("start.query", "Bug in the system");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            // nextBranch 应该是类别 ID，用于路由到对应的输出端口
            assertEquals("technical", result.getNextBranch());
            assertNotNull(result.getOutputs().get("category"));
        }

        @Test
        @DisplayName("不同分类应路由到不同的输出端口")
        void shouldRouteDifferentCategoriesToDifferentPorts() {
            // Test technical
            setupMockModel("technical");
            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node1 = createNode("qc-multi-1", data);
            context.setVariable("query", "Technical issue");
            NodeExecutionResult result1 = agent.execute(node1, context);
            assertEquals("technical", result1.getNextBranch());

            // Test billing
            setupMockModel("billing");
            WorkflowNode node2 = createNode("qc-multi-2", data);
            context.setVariable("query", "Payment issue");
            NodeExecutionResult result2 = agent.execute(node2, context);
            assertEquals("billing", result2.getNextBranch());

            // Test general
            setupMockModel("general");
            WorkflowNode node3 = createNode("qc-multi-3", data);
            context.setVariable("query", "General question");
            NodeExecutionResult result3 = agent.execute(node3, context);
            assertEquals("general", result3.getNextBranch());
        }

        @Test
        @DisplayName("未知分类应失败")
        void shouldFailForUnknownCategory() {
            // Arrange
            setupMockModel("unknown_category_xyz");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-unknown-1", data);
            context.setVariable("start.query", "Random gibberish text");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("未知类别"));
            assertNull(result.getNextBranch());
        }
    }

    @Nested
    @DisplayName("工作流集成场景测试")
    class WorkflowIntegrationScenarioTests {

        @Test
        @DisplayName("应在工作流上下文中正确存储分类结果")
        void shouldStoreClassificationResultInContext() {
            // Arrange
            setupMockModel("billing");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-context-1", data);
            context.setVariable("start.query", "Invoice question");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            // 验证输出包含所有必要信息供下游节点使用
            assertNotNull(result.getOutputs().get("category"));
            assertNotNull(result.getOutputs().get("categoryName"));
            assertNotNull(result.getOutputs().get("categoryDescription"));
            assertNotNull(result.getOutputs().get("inputText"));
            assertNotNull(result.getOutputs().get("rawResponse"));
        }

        @Test
        @DisplayName("应正确处理变量引用解析")
        void shouldResolveVariableReferences() {
            // Arrange
            setupMockModel("technical");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{llm.output}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-varref-1", data);
            // 使用 setVariable 设置变量，因为 agent 使用 context.getVariable 解析变量
            context.setVariable("llm.output", "I have a bug in my code");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("technical", result.getOutputs().get("category"));
            assertEquals("I have a bug in my code", result.getOutputs().get("inputText"));
        }

        @Test
        @DisplayName("应包含 Token 使用统计供监控使用")
        void shouldIncludeTokenUsageForMonitoring() {
            // Arrange
            setupMockModel("general");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-tokens-1", data);
            context.setVariable("start.query", "Hello");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(10, result.getOutputs().get("inputTokens"));
            assertEquals(5, result.getOutputs().get("outputTokens"));
            assertEquals(15, result.getOutputs().get("totalTokens"));
        }
    }

    @Nested
    @DisplayName("多类别场景集成测试")
    class MultiCategoryScenarioTests {

        private List<Map<String, Object>> createExtendedCategories() {
            List<Map<String, Object>> categories = new ArrayList<>();

            String[] categoryData = {
                    "sales,Sales,Questions about purchasing and pricing",
                    "returns,Returns,Questions about returns and refunds",
                    "shipping,Shipping,Questions about delivery and tracking",
                    "account,Account,Questions about account management",
                    "other,Other,Other questions"
            };

            for (String data : categoryData) {
                String[] parts = data.split(",");
                Map<String, Object> cat = new HashMap<>();
                cat.put("id", parts[0]);
                cat.put("name", parts[1]);
                cat.put("description", parts[2]);
                categories.add(cat);
            }

            return categories;
        }

        @Test
        @DisplayName("应正确处理多个类别的分类")
        void shouldHandleMultipleCategories() {
            // Arrange
            setupMockModel("shipping");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{query}}");
            data.put("categories", createExtendedCategories());

            WorkflowNode node = createNode("qc-multi-cat-1", data);
            context.setVariable("query", "Where is my package?");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals("shipping", result.getOutputs().get("category"));
            assertEquals("Shipping", result.getOutputs().get("categoryName"));
            assertEquals("shipping", result.getNextBranch());
        }

        @Test
        @DisplayName("getCategoryIds 应返回所有类别 ID 用于端口生成")
        void shouldReturnAllCategoryIdsForPortGeneration() {
            // Arrange
            QuestionClassifierConfig config = new QuestionClassifierConfig();
            config.setCategories(Arrays.asList(
                    new ClassCategory("cat1", "Category 1", "Desc 1", null),
                    new ClassCategory("cat2", "Category 2", "Desc 2", null),
                    new ClassCategory("cat3", "Category 3", "Desc 3", null),
                    new ClassCategory("cat4", "Category 4", "Desc 4", null),
                    new ClassCategory("cat5", "Category 5", "Desc 5", null)));

            // Act
            List<String> categoryIds = agent.getCategoryIds(config);

            // Assert
            assertEquals(5, categoryIds.size());
            assertTrue(categoryIds.containsAll(Arrays.asList("cat1", "cat2", "cat3", "cat4", "cat5")));
        }
    }

    @Nested
    @DisplayName("错误处理集成测试")
    class ErrorHandlingIntegrationTests {

        @Test
        @DisplayName("空输入应返回失败结果")
        void shouldFailWithEmptyInput() {
            // Arrange
            setupMockModel("technical");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-empty-1", data);
            // 不设置输入变量

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("输入文本为空"));
        }

        @Test
        @DisplayName("空白输入应返回失败结果")
        void shouldFailWithBlankInput() {
            // Arrange
            setupMockModel("technical");

            Map<String, Object> data = new HashMap<>();
            data.put("modelId", 1L);
            data.put("inputVariable", "{{start.query}}");
            data.put("categories", createTechSupportCategories());

            WorkflowNode node = createNode("qc-blank-1", data);
            context.setVariable("start.query", "   ");

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
        }
    }
}
