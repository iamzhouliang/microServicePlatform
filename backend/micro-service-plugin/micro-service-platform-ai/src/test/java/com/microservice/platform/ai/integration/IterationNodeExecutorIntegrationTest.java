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
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.agent.impl.IterationNodeExecutor;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * IterationNodeExecutor 集成测试
 * 测试顺序处理、并行处理和错误处理策略
 * Requirements: 4.4-4.7
 *
 * @author xJh
 * @since 2026/01/08
 */
@DisplayName("IterationNodeExecutor 集成测试")
class IterationNodeExecutorIntegrationTest {

    private IterationNodeExecutor agent;
    private ExecutionContext context;

    @BeforeEach
    void setUp() {
        agent = new IterationNodeExecutor();
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
                .type(NodeType.ITERATION)
                .label("Iteration")
                .data(data)
                .build();
    }

    @Nested
    @DisplayName("顺序处理集成测试")
    class SequentialProcessingIntegrationTests {

        @Test
        @DisplayName("应按顺序处理数组中的每个元素")
        void shouldProcessElementsInOrder() {
            // Arrange
            List<Integer> processOrder = new ArrayList<>();
            agent.setIterationProcessor(item -> {
                processOrder.add(item.index());
                return item.item();
            });

            context.setVariable("items", Arrays.asList("a", "b", "c", "d", "e"));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-seq-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(Arrays.asList(0, 1, 2, 3, 4), processOrder);
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) result.getOutputs().get("results");
            assertEquals(5, results.size());
        }

        @Test
        @DisplayName("应正确设置 item 和 index 内置变量")
        void shouldSetItemAndIndexVariables() {
            // Arrange
            List<String> capturedItems = new ArrayList<>();
            List<Integer> capturedIndexes = new ArrayList<>();

            agent.setIterationProcessor(item -> {
                capturedItems.add(String.valueOf(item.item()));
                capturedIndexes.add(item.index());
                return Map.of("processed", item.item(), "idx", item.index());
            });

            context.setVariable("data", Arrays.asList("first", "second", "third"));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{data}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-vars-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(Arrays.asList("first", "second", "third"), capturedItems);
            assertEquals(Arrays.asList(0, 1, 2), capturedIndexes);
        }

        @Test
        @DisplayName("应处理复杂对象数组")
        void shouldProcessComplexObjectArray() {
            // Arrange
            agent.setIterationProcessor(item -> {
                @SuppressWarnings("unchecked")
                Map<String, Object> obj = (Map<String, Object>) item.item();
                return Map.of(
                        "id", obj.get("id"),
                        "processed", true,
                        "index", item.index());
            });

            List<Map<String, Object>> items = Arrays.asList(
                    Map.of("id", 1, "name", "Item 1"),
                    Map.of("id", 2, "name", "Item 2"),
                    Map.of("id", 3, "name", "Item 3"));
            context.setVariable("items", items);

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-complex-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> results = (List<Map<String, Object>>) result.getOutputs().get("results");
            assertEquals(3, results.size());
            assertEquals(1, results.get(0).get("id"));
            assertEquals(true, results.get(0).get("processed"));
        }

        @Test
        @DisplayName("应正确处理嵌套数组")
        void shouldProcessNestedArrays() {
            // Arrange
            agent.setIterationProcessor(item -> {
                @SuppressWarnings("unchecked")
                List<Integer> subList = (List<Integer>) item.item();
                return subList.stream().mapToInt(Integer::intValue).sum();
            });

            List<List<Integer>> nestedArray = Arrays.asList(
                    Arrays.asList(1, 2, 3),
                    Arrays.asList(4, 5),
                    Arrays.asList(6, 7, 8, 9));
            context.setVariable("nested", nestedArray);

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{nested}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-nested-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Integer> results = (List<Integer>) result.getOutputs().get("results");
            assertEquals(Arrays.asList(6, 9, 30), results);
        }
    }

    @Nested
    @DisplayName("并行处理集成测试")
    class ParallelProcessingIntegrationTests {

        @Test
        @DisplayName("应并行处理数组元素")
        void shouldProcessElementsInParallel() {
            // Arrange
            Set<String> threadNames = ConcurrentHashMap.newKeySet();
            agent.setIterationProcessor(item -> {
                threadNames.add(Thread.currentThread().getName());
                try {
                    // 模拟处理时间
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return item.item();
            });

            context.setVariable("items", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "PARALLEL");
            data.put("parallelCount", 4);

            WorkflowNode node = createNode("iter-parallel-1", data);

            // Act
            long startTime = System.currentTimeMillis();
            NodeExecutionResult result = agent.execute(node, context);
            long duration = System.currentTimeMillis() - startTime;

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) result.getOutputs().get("results");
            assertEquals(8, results.size());
            // 并行处理应该比顺序处理快
            assertTrue(duration < 400, "Parallel processing should be faster than sequential");
        }

        @Test
        @DisplayName("并行处理应保持结果顺序")
        void shouldMaintainResultOrderInParallel() {
            // Arrange
            agent.setIterationProcessor(item -> {
                // 模拟不同的处理时间
                try {
                    Thread.sleep((10 - item.index()) * 10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "item-" + item.index();
            });

            context.setVariable("items", Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "PARALLEL");
            data.put("parallelCount", 5);

            WorkflowNode node = createNode("iter-order-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<String> results = (List<String>) result.getOutputs().get("results");
            assertEquals(10, results.size());
            // 验证顺序保持
            for (int i = 0; i < 10; i++) {
                assertEquals("item-" + i, results.get(i));
            }
        }

        @Test
        @DisplayName("应尊重 parallelCount 配置")
        void shouldRespectParallelCountConfig() {
            // Arrange
            AtomicInteger maxConcurrent = new AtomicInteger(0);
            AtomicInteger currentConcurrent = new AtomicInteger(0);

            agent.setIterationProcessor(item -> {
                int current = currentConcurrent.incrementAndGet();
                maxConcurrent.updateAndGet(max -> Math.max(max, current));
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                currentConcurrent.decrementAndGet();
                return item.item();
            });

            context.setVariable("items", Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "PARALLEL");
            data.put("parallelCount", 3);

            WorkflowNode node = createNode("iter-count-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertTrue(maxConcurrent.get() <= 3, "Max concurrent should not exceed parallelCount");
        }
    }

    @Nested
    @DisplayName("错误处理集成测试")
    class IterationFailureIntegrationTests {

        @Test
        @DisplayName("任意元素失败时应立即终止")
        void shouldTerminateOnFirstError() {
            // Arrange
            AtomicInteger processedCount = new AtomicInteger(0);
            agent.setIterationProcessor(item -> {
                processedCount.incrementAndGet();
                if (item.index() == 2) {
                    throw new RuntimeException("Error at index 2");
                }
                return item.item();
            });

            context.setVariable("items", Arrays.asList(1, 2, 3, 4, 5));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-terminate-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("terminated"));
            // 只处理了 0, 1, 2
            assertEquals(3, processedCount.get());
        }
    }

    @Nested
    @DisplayName("工作流集成场景测试")
    class WorkflowIntegrationScenarioTests {

        @Test
        @DisplayName("应正确解析上游节点的数组变量")
        void shouldResolveUpstreamArrayVariable() {
            // Arrange
            agent.setIterationProcessor(item -> "processed-" + item.item());

            // 使用 setVariable 设置变量，因为 resolveArrayVariable 使用 context.getVariable
            context.setVariable("llm.items", Arrays.asList("apple", "banana", "cherry"));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{llm.items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-upstream-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<String> results = (List<String>) result.getOutputs().get("results");
            assertEquals(3, results.size());
            assertEquals("processed-apple", results.get(0));
        }

        @Test
        @DisplayName("应正确设置输出变量供下游节点使用")
        void shouldSetOutputVariableForDownstream() {
            // Arrange
            agent.setIterationProcessor(item -> Map.of("value", item.item(), "doubled", (int) item.item() * 2));

            context.setVariable("numbers", Arrays.asList(1, 2, 3));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{numbers}}");
            data.put("processingMode", "SEQUENTIAL");
            data.put("outputVariable", "processedNumbers");

            WorkflowNode node = createNode("iter-output-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertNotNull(result.getOutputs().get("processedNumbers"));
            assertNotNull(result.getOutputs().get("results"));
            assertNotNull(result.getOutputs().get("totalCount"));
            assertNotNull(result.getOutputs().get("successCount"));
            assertNotNull(result.getOutputs().get("failedCount"));
        }

        @Test
        @DisplayName("成功执行应包含完整统计信息")
        void shouldIncludeCompleteStatistics() {
            // Arrange
            agent.setIterationProcessor(IterationNodeExecutor.IterationItem::item);

            context.setVariable("items", Arrays.asList(1, 2, 3, 4, 5));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-stats-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            assertEquals(5, result.getOutputs().get("totalCount"));
            assertEquals(5, result.getOutputs().get("successCount"));
            assertEquals(0, result.getOutputs().get("failedCount"));
        }
    }

    @Nested
    @DisplayName("边界条件集成测试")
    class BoundaryConditionIntegrationTests {

        @Test
        @DisplayName("空数组应返回空结果")
        void shouldReturnEmptyResultsForEmptyArray() {
            // Arrange
            context.setVariable("items", Collections.emptyList());

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-empty-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) result.getOutputs().get("results");
            assertTrue(results.isEmpty());
            assertEquals(0, result.getOutputs().get("totalCount"));
        }

        @Test
        @DisplayName("单元素数组应正确处理")
        void shouldProcessSingleElementArray() {
            // Arrange
            agent.setIterationProcessor(item -> "single-" + item.item());

            context.setVariable("items", Collections.singletonList("only"));

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-single-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<String> results = (List<String>) result.getOutputs().get("results");
            assertEquals(1, results.size());
            assertEquals("single-only", results.get(0));
        }

        @Test
        @DisplayName("超过最大迭代次数应失败")
        void shouldFailWhenExceedingMaxIterations() {
            // Arrange
            List<Integer> largeArray = new ArrayList<>();
            for (int i = 0; i < 100; i++) {
                largeArray.add(i);
            }
            context.setVariable("items", largeArray);

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{items}}");
            data.put("processingMode", "SEQUENTIAL");
            data.put("maxIterations", 50);

            WorkflowNode node = createNode("iter-max-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertFalse(result.isSuccess());
            assertTrue(result.getErrorMessage().contains("exceeds"));
        }

        @Test
        @DisplayName("null 数组变量应返回空结果")
        void shouldReturnEmptyResultsForNullArray() {
            // Arrange - 不设置变量

            Map<String, Object> data = new HashMap<>();
            data.put("arrayVariable", "{{nonexistent}}");
            data.put("processingMode", "SEQUENTIAL");

            WorkflowNode node = createNode("iter-null-1", data);

            // Act
            NodeExecutionResult result = agent.execute(node, context);

            // Assert
            assertTrue(result.isSuccess());
            @SuppressWarnings("unchecked")
            List<Object> results = (List<Object>) result.getOutputs().get("results");
            assertTrue(results.isEmpty());
        }
    }
}
