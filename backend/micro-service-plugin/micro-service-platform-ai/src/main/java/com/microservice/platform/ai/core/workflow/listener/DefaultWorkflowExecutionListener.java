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

package com.microservice.platform.ai.core.workflow.listener;

import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.definition.WorkflowRuntimeEventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 默认工作流执行监听器实现
 * 通过 ExecutionContext 的 SSE 推送方法将事件推送到前端
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
public class DefaultWorkflowExecutionListener implements WorkflowExecutionListener {

    @Override
    public void onExecutionStarted(ExecutionContext context, Map<String, Object> inputs) {
        log.info("工作流执行开始: {}", context.getExecutionId());
        context.pushExecutionStarted(inputs);
    }

    @Override
    public void onExecutionCompleted(ExecutionContext context, Map<String, Object> outputs, long duration) {
        log.info("工作流执行完成: {}, 耗时: {}ms", context.getExecutionId(), duration);
        context.pushExecutionCompleted(outputs, duration);
        context.completeSse();
    }

    @Override
    public void onExecutionFailed(ExecutionContext context, String errorMessage) {
        log.error("工作流执行失败: {}, 错误: {}", context.getExecutionId(), errorMessage);
        context.pushExecutionFailed(errorMessage);
        context.completeSse();
    }

    @Override
    public void onExecutionPaused(ExecutionContext context, String currentNodeId) {
        log.info("工作流执行已暂停: {}, 当前节点: {}", context.getExecutionId(), currentNodeId);
        context.pushEvent(WorkflowRuntimeEventType.WORKFLOW_PAUSED.code(), Map.of(
                "executionId", context.getExecutionId(),
                "nodeId", currentNodeId,
                "timestamp", System.currentTimeMillis()));
    }

    @Override
    public void onExecutionResumed(ExecutionContext context) {
        log.info("工作流执行已恢复: {}", context.getExecutionId());
        context.pushExecutionResumed();
    }

    @Override
    public void onExecutionCancelled(ExecutionContext context) {
        log.info("工作流执行已取消: {}", context.getExecutionId());
        context.pushExecutionCancelled("用户取消执行");
        context.completeSse();
    }

    @Override
    public void onNodeStarted(ExecutionContext context, String nodeId, Map<String, Object> input) {
        log.debug("节点开始执行: {}, 执行ID: {}", nodeId, context.getExecutionId());
        context.pushNodeStarted(nodeId, input);
    }

    @Override
    public void onNodeCompleted(ExecutionContext context, String nodeId, Map<String, Object> output, Long duration) {
        log.debug("节点执行完成: {}, 执行ID: {}, 耗时: {}ms",
                nodeId, context.getExecutionId(), duration);
        context.pushNodeCompleted(nodeId, output, duration);
    }

    @Override
    public void onNodeFailed(ExecutionContext context, String nodeId, String errorMessage) {
        log.error("节点执行失败: {}, 执行ID: {}, 错误: {}",
                nodeId, context.getExecutionId(), errorMessage);
        context.pushNodeFailed(nodeId, errorMessage);
    }

    @Override
    public void onStreamToken(ExecutionContext context, String nodeId, String token) {
        // 流式 Token 不记录日志，避免日志过多
        context.pushStreamToken(nodeId, token);
    }

    @Override
    public void onBreakpointHit(ExecutionContext context, String nodeId) {
        log.info("命中断点: {}, 执行ID: {}", nodeId, context.getExecutionId());
        context.pushBreakpointHit(nodeId);
    }

    @Override
    public void onVariableUpdated(ExecutionContext context, String variableName, Object value) {
        log.debug("变量已更新: {} = {}, 执行ID: {}",
                variableName, value, context.getExecutionId());
    }
}
