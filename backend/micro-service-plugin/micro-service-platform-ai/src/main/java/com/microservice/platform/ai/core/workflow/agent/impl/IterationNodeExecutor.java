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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.IterationNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.IterationNodeConfig.ProcessingMode;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

/**
 * 迭代节点执行器
 * 对数组元素进行批量处理，支持顺序和并行模式
 * 特性:
 * - 使用 IterationNodeConfig 强类型配置
 * - 支持顺序处理模式 (SEQUENTIAL)
 * - 支持并行处理模式 (PARALLEL) 使用线程池
 * - 提供 item 和 index 内置变量
 * - 任意元素失败立即终止
 * 内置变量:
 * - item: 当前迭代元素
 * - index: 当前索引 (从 0 开始)
 *
 * @author xJh
 * @since 2026/01/08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IterationNodeExecutor extends AbstractNodeExecutor {

    /**
     * 默认最大迭代次数
     */
    private static final int DEFAULT_MAX_ITERATIONS = 1000;

    /**
     * 默认并行数量
     */
    private static final int DEFAULT_PARALLEL_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * 默认迭代超时时间 (毫秒)
     */
    private static final long DEFAULT_ITERATION_TIMEOUT = 60000L;

    /**
     * 共享线程池，用于并行迭代执行
     */
    private static final ExecutorService SHARED_EXECUTOR = Executors.newWorkStealingPool();

    /**
     * 迭代处理器 - 用于处理每个迭代元素
     * 可以通过依赖注入设置，用于执行子流程
     * @param arrayVariable arrayVariable 参数
     * @param context 执行上下文
     * @return 处理结果
     */
    private Function<IterationItem, Object> iterationProcessor;

    @Override
    public NodeType getType() {
        return NodeType.ITERATION;
    }

    @Override
    public void validate(WorkflowNode node) {
        IterationNodeConfig config = parseConfig(node, IterationNodeConfig.class);

        if (config == null) {
            throw new IllegalArgumentException("ITERATION 节点缺少配置");
        }

        if (config.getArrayVariable() == null || config.getArrayVariable().isBlank()) {
            throw new IllegalArgumentException("ITERATION 节点缺少 arrayVariable 配置");
        }

        if (config.getMaxIterations() != null && config.getMaxIterations() <= 0) {
            throw new IllegalArgumentException("maxIterations 必须为正数");
        }

        if (config.getParallelCount() != null && config.getParallelCount() <= 0) {
            throw new IllegalArgumentException("parallelCount 必须为正数");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 ITERATION 节点: {}", node.getId());

        IterationNodeConfig config = parseConfig(node, IterationNodeConfig.class);

        // Get the array to iterate
        List<?> array = resolveArrayVariable(config.getArrayVariable(), context);

        if (array == null || array.isEmpty()) {
            log.debug("ITERATION 节点 {} 输入数组为空，返回空结果", node.getId());
            return createSuccessResult(node, config, Collections.emptyList(), 0);
        }

        // Check max iterations limit
        int maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : DEFAULT_MAX_ITERATIONS;

        if (array.size() > maxIterations) {
            return NodeExecutionResult.failure(
                    String.format("Array size (%d) exceeds max iterations limit (%d)",
                            array.size(), maxIterations));
        }

        // Execute iterations
        ProcessingMode mode = config.getProcessingMode() != null ? config.getProcessingMode() : ProcessingMode.SEQUENTIAL;

        List<Object> results;

        try {
            if (mode == ProcessingMode.PARALLEL) {
                IterationResults iterResults = executeParallel(array, node, context, config);
                results = iterResults.results;
            } else {
                IterationResults iterResults = executeSequential(array, node, context, config);
                results = iterResults.results;
            }
        } catch (IterationTerminatedException e) {
            return NodeExecutionResult.failure(
                    String.format("Iteration terminated at index %d: %s", e.index, e.getMessage()));
        }

        return createSuccessResult(node, config, results, array.size());
    }

    /**
     * Resolve array variable from context
     * @param context 执行上下文
     * @param arrayVariable arrayVariable 参数
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    private List<?> resolveArrayVariable(String arrayVariable, ExecutionContext context) {
        // Remove {{ and }} if present
        String varPath = arrayVariable.trim();
        if (varPath.startsWith("{{") && varPath.endsWith("}}")) {
            varPath = varPath.substring(2, varPath.length() - 2).trim();
        }

        Object value = context.getVariable(varPath, Object.class);

        if (value == null) {
            return null;
        }

        if (value instanceof List) {
            return (List<?>) value;
        }

        if (value.getClass().isArray()) {
            return Arrays.asList((Object[]) value);
        }

        // Single value - wrap in list
        return Collections.singletonList(value);
    }

    /**
     * Execute iterations sequentially
     * @param config 节点配置
     * @param context 执行上下文
     * @param array array 参数
     * @param node 工作流节点
     * @return 处理结果
     * @throws IterationTerminatedException 处理失败时抛出
     */
    private IterationResults executeSequential(List<?> array, WorkflowNode node,
                                               ExecutionContext context, IterationNodeConfig config) {

        List<Object> results = new ArrayList<>();

        for (int i = 0; i < array.size(); i++) {
            Object item = array.get(i);

            // Check if execution should stop
            if (context.shouldStop()) {
                throw new IterationTerminatedException(i, "执行已取消");
            }

            try {
                // Set iteration variables
                context.setVariable("item", item);
                context.setVariable("index", i);
                context.setVariable(node.getId() + ".item", item);
                context.setVariable(node.getId() + ".index", i);

                // Execute iteration body
                Object result = executeIterationBody(item, i, node, context);
                results.add(result);

                log.trace("ITERATION 节点 {} 已处理索引 {} 的元素: {}", node.getId(), i, item);

            } catch (Exception e) {
                log.warn("ITERATION 节点 {} 在索引 {} 处理失败: {}", node.getId(), i, e.getMessage());
                throw new IterationTerminatedException(i, e.getMessage());
            }
        }

        return new IterationResults(results);
    }

    /**
     * Execute iterations in parallel
     * @param config 节点配置
     * @param context 执行上下文
     * @param array array 参数
     * @param node 工作流节点
     * @return 处理结果
     */
    private IterationResults executeParallel(List<?> array, WorkflowNode node,
                                             ExecutionContext context, IterationNodeConfig config) {

        int parallelCount = config.getParallelCount() != null ? config.getParallelCount() : DEFAULT_PARALLEL_COUNT;

        long timeout = config.getIterationTimeout() != null ? config.getIterationTimeout() : DEFAULT_ITERATION_TIMEOUT;

        // 使用共享线程池，避免每次创建新线程池
        List<Future<IndexedResult>> futures = new ArrayList<>();
        Semaphore semaphore = new Semaphore(parallelCount);

        try {
            // Submit all tasks
            for (int i = 0; i < array.size(); i++) {
                final int index = i;
                final Object item = array.get(i);

                Callable<IndexedResult> task = () -> {
                    boolean acquired = false;
                    try {
                        semaphore.acquire();
                        acquired = true;

                        // Create child context for parallel execution
                        ExecutionContext childContext = createChildContext(context);
                        childContext.setVariable("item", item);
                        childContext.setVariable("index", index);
                        childContext.setVariable(node.getId() + ".item", item);
                        childContext.setVariable(node.getId() + ".index", index);

                        Object result = executeIterationBody(item, index, node, childContext);
                        return new IndexedResult(index, result, null);
                    } catch (Exception e) {
                        return new IndexedResult(index, null, e);
                    } finally {
                        if (acquired) {
                            semaphore.release();
                        }
                    }
                };
                futures.add(SHARED_EXECUTOR.submit(task));
            }

            // Collect results
            return collectParallelResults(futures, array.size(), timeout, node.getId());
        } catch (Exception e) {
            log.error("并行迭代执行失败: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * 收集并行执行结果
     * @param nodeId 节点标识
     * @param timeout 超时时间
     * @param arraySize arraySize 参数
     * @param futures futures 参数
     * @return 处理结果
     * @throws IterationTerminatedException 处理失败时抛出
     * @throws IterationTerminatedException 处理失败时抛出
     */
    private IterationResults collectParallelResults(List<Future<IndexedResult>> futures, int arraySize,
                                                    long timeout, String nodeId) {
        List<Object> results = new ArrayList<>(Collections.nCopies(arraySize, null));

        for (int i = 0; i < futures.size(); i++) {
            try {
                IndexedResult indexedResult = futures.get(i).get(timeout, TimeUnit.MILLISECONDS);
                handleIndexedResult(indexedResult, results, nodeId);
            } catch (TimeoutException e) {
                log.warn("ITERATION 节点 {} 的并行任务 {} 超时", nodeId, i);
                throw new IterationTerminatedException(i, "Timeout");
            } catch (InterruptedException | ExecutionException e) {
                log.warn("ITERATION 节点 {} 的并行任务 {} 异常: {}", nodeId, i, e.getMessage());
                throw new IterationTerminatedException(i, e.getMessage());
            }
        }

        return new IterationResults(results);
    }

    /**
     * 处理单个索引结果
     * @param indexedResult indexedResult 参数
     * @param nodeId 节点标识
     * @param results 处理结果集合
     * @throws IterationTerminatedException 处理失败时抛出
     */
    private void handleIndexedResult(IndexedResult indexedResult, List<Object> results, String nodeId) {
        if (indexedResult.error != null) {
            log.warn("ITERATION 节点 {} 的并行任务 {} 失败: {}",
                    nodeId, indexedResult.index, indexedResult.error.getMessage());
            throw new IterationTerminatedException(indexedResult.index, indexedResult.error.getMessage());
        }
        results.set(indexedResult.index, indexedResult.result);
    }

    /**
     * Execute the iteration body for a single item
     * This is a placeholder - in real implementation, this would execute sub-workflow
     * @param context 执行上下文
     * @param index 索引位置
     * @param item item 参数
     * @param node 工作流节点
     * @return 处理结果
     */
    private Object executeIterationBody(Object item, int index, WorkflowNode node, ExecutionContext context) {
        // If a custom processor is set, use it
        if (iterationProcessor != null) {
            return iterationProcessor.apply(new IterationItem(item, index, node.getId()));
        }

        // Default behavior: return the item with index info
        Map<String, Object> result = new HashMap<>();
        result.put("item", item);
        result.put("index", index);
        result.put("processed", true);
        return result;
    }

    /**
     * Create a child context for parallel execution
     * @param parent parent 参数
     * @return 处理结果
     */
    private ExecutionContext createChildContext(ExecutionContext parent) {
        return ExecutionContext.builder()
                .executionId(parent.getExecutionId() + "-child-" + UUID.randomUUID().toString().substring(0, 8))
                .workflowId(parent.getWorkflowId())
                .workflowVersion(parent.getWorkflowVersion())
                .userId(parent.getUserId())
                .tenantId(parent.getTenantId())
                .variables(new ConcurrentHashMap<>(parent.getVariables()))
                .build();
    }

    /**
     * Create success result
     * @param results 处理结果集合
     * @param totalCount totalCount 参数
     * @param config 节点配置
     * @param node 工作流节点
     * @return 处理结果
     */
    private NodeExecutionResult createSuccessResult(WorkflowNode node, IterationNodeConfig config,
                                                    List<Object> results, int totalCount) {

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("results", results);
        outputs.put("result", results);
        outputs.put("totalCount", totalCount);
        outputs.put("successCount", totalCount);
        outputs.put("failedCount", 0);

        // Set output variable if configured
        String outputVariable = config.getOutputVariable();
        if (outputVariable != null && !outputVariable.isBlank()) {
            outputs.put(outputVariable, results);
        }

        log.info("ITERATION 节点 {} 执行完成: 总数={}，成功={}，失败={}",
                node.getId(), totalCount, totalCount, 0);

        return NodeExecutionResult.success(outputs);
    }

    /**
     * Set custom iteration processor
     * @param processor processor 参数
     */
    public void setIterationProcessor(Function<IterationItem, Object> processor) {
        this.iterationProcessor = processor;
    }

    /**
     * Iteration item wrapper
     * @param index 索引位置
     * @param item item 参数
     * @param nodeId 节点标识
     */
    public record IterationItem(Object item, int index, String nodeId) {
    }

    /**
     * Indexed result for parallel execution
     * @param error error 参数
     * @param index 索引位置
     * @param result 处理结果
     */
    private record IndexedResult(int index, Object result, Exception error) {
    }

    /**
     * Iteration results wrapper
     * @param results 处理结果集合
     */
    private record IterationResults(List<Object> results) {
    }

    /**
     * Exception for iteration termination
     */
    private static class IterationTerminatedException extends RuntimeException {

        private final int index;

        IterationTerminatedException(int index, String message) {
            super(message);
            this.index = index;
        }
    }
}
