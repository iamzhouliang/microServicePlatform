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
import com.microservice.platform.ai.core.workflow.config.node.LoopNodeConfig;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.expression.ExpressionEvaluator;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 循环节点执行器
 * 控制循环执行，支持条件退出和最大迭代次数限制
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoopNodeExecutor extends AbstractNodeExecutor {

    /**
     * 循环计数器变量名前缀
     */
    private static final String LOOP_COUNTER_PREFIX = "_loop_counter_";

    /**
     * 默认最大迭代次数
     * @param context 执行上下文
     * @param node 工作流节点
     * @return 处理结果
     */
    private static final int DEFAULT_MAX_ITERATIONS = 100;

    private final ExpressionEvaluator expressionEvaluator;

    @Override
    public NodeType getType() {
        return NodeType.LOOP;
    }

    @Override
    public void validate(WorkflowNode node) {
        LoopNodeConfig config = parseConfig(node, LoopNodeConfig.class);
        if (config.getMaxIterations() != null && config.getMaxIterations() <= 0) {
            throw new IllegalArgumentException("循环节点 maxIterations 必须大于 0");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行循环节点: {}", node.getId());

        LoopNodeConfig config = parseConfig(node, LoopNodeConfig.class);
        Integer maxIterations = config.getMaxIterations() != null ? config.getMaxIterations() : DEFAULT_MAX_ITERATIONS;
        String exitCondition = config.getExitCondition();
        final String loopVariable = config.getLoopVariable();

        // 获取或初始化循环计数器
        String counterKey = LOOP_COUNTER_PREFIX + node.getId();
        Integer currentIteration = context.getVariable(counterKey, Integer.class, 0);

        // 检查是否达到最大迭代次数
        if (currentIteration >= maxIterations) {
            log.debug("循环节点 {} 已达到最大迭代次数: {}", node.getId(), maxIterations);
            return createLoopExitResult(node, context, currentIteration, "max_iterations_reached");
        }

        // 评估退出条件
        if (exitCondition != null && !exitCondition.isEmpty()) {
            try {
                String resolvedCondition = resolveTemplate(exitCondition, context);
                Map<String, Object> variables = context.getAllVariables();
                boolean shouldExit = expressionEvaluator.evaluate(resolvedCondition, variables);

                if (shouldExit) {
                    log.debug("循环节点 {} 在第 {} 次迭代满足退出条件", node.getId(), currentIteration);
                    return createLoopExitResult(node, context, currentIteration, "exit_condition_met");
                }
            } catch (Exception e) {
                log.warn("评估循环节点 {} 退出条件失败: {}", node.getId(), e.getMessage());
            }
        }

        // 更新循环计数器
        int nextIteration = currentIteration + 1;
        context.setVariable(counterKey, nextIteration);

        // 设置循环变量（如果配置了）
        if (loopVariable != null && !loopVariable.isEmpty()) {
            context.setVariable(loopVariable, nextIteration);
        }

        // 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("iteration", nextIteration);
        outputs.put("maxIterations", maxIterations);
        outputs.put("continueLoop", true);

        log.debug("循环节点 {} 当前迭代 {}/{}", node.getId(), nextIteration, maxIterations);
        return NodeExecutionResult.loop(true, outputs);
    }

    /**
     * 创建循环退出结果
     * @param iterations iterations 参数
     * @param reason reason 参数
     * @param context 执行上下文
     * @param node 工作流节点
     * @return 处理结果
     */
    private NodeExecutionResult createLoopExitResult(WorkflowNode node, ExecutionContext context,
                                                     int iterations, String reason) {
        // 清理循环计数器
        String counterKey = LOOP_COUNTER_PREFIX + node.getId();
        context.getVariables().remove(counterKey);

        Map<String, Object> outputs = new HashMap<>();
        outputs.put("iteration", iterations);
        outputs.put("exitReason", reason);
        outputs.put("continueLoop", false);

        return NodeExecutionResult.loop(false, outputs);
    }
}
