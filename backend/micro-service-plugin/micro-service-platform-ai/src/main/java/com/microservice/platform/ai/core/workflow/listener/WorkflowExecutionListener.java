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

import java.util.Map;

/**
 * 工作流执行监听器接口
 * 用于监听工作流执行过程中的各种事件，并通过 SSE 推送到前端
 *
 * @author xJh
 * @since 2026/01/07
 */
public interface WorkflowExecutionListener {

    /**
     * 工作流执行开始
     *
     * @param context 执行上下文
     * @param inputs  输入参数
     */
    void onExecutionStarted(ExecutionContext context, Map<String, Object> inputs);

    /**
     * 工作流执行完成
     *
     * @param context  执行上下文
     * @param outputs  输出结果
     * @param duration 执行耗时（毫秒）
     */
    void onExecutionCompleted(ExecutionContext context, Map<String, Object> outputs, long duration);

    /**
     * 工作流执行失败
     *
     * @param context      执行上下文
     * @param errorMessage 错误信息
     */
    void onExecutionFailed(ExecutionContext context, String errorMessage);

    /**
     * 工作流执行暂停
     *
     * @param context       执行上下文
     * @param currentNodeId 当前节点 ID
     */
    void onExecutionPaused(ExecutionContext context, String currentNodeId);

    /**
     * 工作流执行恢复
     *
     * @param context 执行上下文
     */
    void onExecutionResumed(ExecutionContext context);

    /**
     * 工作流执行取消
     *
     * @param context 执行上下文
     */
    void onExecutionCancelled(ExecutionContext context);

    /**
     * 节点开始执行
     *
     * @param context 执行上下文
     * @param nodeId  节点 ID
     * @param input   节点输入
     */
    void onNodeStarted(ExecutionContext context, String nodeId, Map<String, Object> input);

    /**
     * 节点执行完成
     *
     * @param context  执行上下文
     * @param nodeId   节点 ID
     * @param output   节点输出
     * @param duration 执行耗时（毫秒）
     */
    void onNodeCompleted(ExecutionContext context, String nodeId, Map<String, Object> output, Long duration);

    /**
     * 节点执行失败
     *
     * @param context      执行上下文
     * @param nodeId       节点 ID
     * @param errorMessage 错误信息
     */
    void onNodeFailed(ExecutionContext context, String nodeId, String errorMessage);

    /**
     * 流式 Token 输出（LLM 节点）
     *
     * @param context 执行上下文
     * @param nodeId  节点 ID
     * @param token   Token 内容
     */
    void onStreamToken(ExecutionContext context, String nodeId, String token);

    /**
     * 断点命中
     *
     * @param context 执行上下文
     * @param nodeId  节点 ID
     */
    void onBreakpointHit(ExecutionContext context, String nodeId);

    /**
     * 变量更新（调试模式）
     *
     * @param context      执行上下文
     * @param variableName 变量名
     * @param value        变量值
     */
    void onVariableUpdated(ExecutionContext context, String variableName, Object value);
}
