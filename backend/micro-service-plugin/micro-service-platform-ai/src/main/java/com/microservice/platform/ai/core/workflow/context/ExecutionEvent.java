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

package com.microservice.platform.ai.core.workflow.context;

import com.microservice.platform.ai.core.workflow.definition.WorkflowRuntimeEventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 工作流执行事件
 * 用于 SSE 推送的事件数据结构
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionEvent {

    /**
     * 事件类型
     */
    private String type;

    /**
     * 执行 ID
     */
    private String executionId;

    /**
     * 节点 ID（节点相关事件）
     */
    private String nodeId;

    /**
     * 事件数据
     */
    private Map<String, Object> data;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 时间戳
     * @param executionId 执行标识
     * @param inputs 输入参数
     * @param inputs 输入参数
     * @return 处理结果
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    // ==================== 工厂方法 ====================

    /**
     * 创建执行开始事件
     * @param executionId 执行标识
     * @param inputs 输入参数
     * @return 处理结果
     */
    public static ExecutionEvent executionStarted(String executionId, Map<String, Object> inputs) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_STARTED.code())
                .executionId(executionId)
                .data(inputs)
                .build();
    }

    /**
     * 创建执行完成事件
     * @param executionId 执行标识
     * @param duration 持续时间
     * @param outputs 输出数据
     * @return 处理结果
     */
    public static ExecutionEvent executionCompleted(String executionId, Map<String, Object> outputs,
                                                    long duration) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_COMPLETED.code())
                .executionId(executionId)
                .data(Map.of("outputs", outputs, "duration", duration))
                .build();
    }

    /**
     * 创建执行失败事件
     * @param executionId 执行标识
     * @param errorMessage 错误信息
     * @return 处理结果
     */
    public static ExecutionEvent executionFailed(String executionId, String errorMessage) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_FAILED.code())
                .executionId(executionId)
                .error(errorMessage)
                .build();
    }

    /**
     * 创建节点开始事件
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param input 输入数据
     * @return 处理结果
     */
    public static ExecutionEvent nodeStarted(String executionId, String nodeId, Map<String, Object> input) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.NODE_STARTED.code())
                .executionId(executionId)
                .nodeId(nodeId)
                .data(input)
                .build();
    }

    /**
     * 创建节点完成事件
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param duration 持续时间
     * @param output output 参数
     * @return 处理结果
     */
    public static ExecutionEvent nodeCompleted(String executionId, String nodeId,
                                               Map<String, Object> output, Long duration) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.NODE_COMPLETED.code())
                .executionId(executionId)
                .nodeId(nodeId)
                .data(Map.of("output", output != null ? output : Map.of(), "duration", duration != null ? duration : 0))
                .build();
    }

    /**
     * 创建节点失败事件
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param errorMessage 错误信息
     * @return 处理结果
     */
    public static ExecutionEvent nodeFailed(String executionId, String nodeId, String errorMessage) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.NODE_FAILED.code())
                .executionId(executionId)
                .nodeId(nodeId)
                .error(errorMessage)
                .build();
    }

    /**
     * 创建流式 Token 事件
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param token token 参数
     * @return 处理结果
     */
    public static ExecutionEvent streamToken(String executionId, String nodeId, String token) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.NODE_DELTA.code())
                .executionId(executionId)
                .nodeId(nodeId)
                .data(Map.of("token", token))
                .build();
    }

    /**
     * 创建断点命中事件
     * @param variables 变量集合
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @return 处理结果
     */
    public static ExecutionEvent breakpointHit(String executionId, String nodeId,
                                               Map<String, Object> variables) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_PAUSED.code())
                .executionId(executionId)
                .nodeId(nodeId)
                .data(variables)
                .build();
    }

    /**
     * 创建执行暂停事件
     * @param currentNodeId currentNodeId 参数
     * @param executionId 执行标识
     * @return 处理结果
     */
    public static ExecutionEvent executionPaused(String executionId, String currentNodeId) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_PAUSED.code())
                .executionId(executionId)
                .nodeId(currentNodeId)
                .build();
    }

    /**
     * 创建执行恢复事件
     * @param executionId 执行标识
     * @return 处理结果
     */
    public static ExecutionEvent executionResumed(String executionId) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_RESUMED.code())
                .executionId(executionId)
                .build();
    }

    /**
     * 创建执行取消事件
     * @param executionId 执行标识
     * @return 处理结果
     */
    public static ExecutionEvent executionCancelled(String executionId) {
        return ExecutionEvent.builder()
                .type(WorkflowRuntimeEventType.WORKFLOW_CANCELLED.code())
                .executionId(executionId)
                .data(Map.of("reason", "用户取消执行"))
                .build();
    }
}
