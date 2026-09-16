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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.ExecutionStatus;
import com.microservice.platform.ai.core.workflow.definition.WorkflowRuntimeEventType;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行上下文
 * 封装工作流执行过程中的状态和变量传递，支持 SSE 事件推送
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Data
@Builder
public class ExecutionContext {

    /**
     * JSON 序列化器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 执行 ID
     */
    private String executionId;

    /**
     * 工作流 ID
     */
    private Long workflowId;

    /**
     * 工作流版本
     */
    private Integer workflowVersion;

    /**
     * 用户 ID
     */
    private Long userId;

    /**
     * 租户 ID
     */
    private Long tenantId;

    /** 工作流发起时由认证层写入的权限快照。 */
    @Builder.Default
    private Set<String> permissions = Set.of();

    /**
     * 变量存储（线程安全）
     */
    @Builder.Default
    private Map<String, Object> variables = new ConcurrentHashMap<>();

    /**
     * 节点输出存储（线程安全）
     * 存储每个节点的完整输出，用于变量引用解析
     * Key: nodeId, Value: 节点输出 Map
     */
    @Builder.Default
    private Map<String, Object> nodeOutputs = new ConcurrentHashMap<>();

    /**
     * 节点执行状态
     */
    @Builder.Default
    private Map<String, NodeExecutionState> nodeStates = new ConcurrentHashMap<>();

    /**
     * SSE 发射器（用于实时推送事件）
     */
    private SseEmitter sseEmitter;

    /**
     * 断点集合
     */
    @Builder.Default
    private Set<String> breakpoints = ConcurrentHashMap.newKeySet();

    /**
     * 是否暂停
     */
    @Builder.Default
    private volatile boolean paused;

    /**
     * 是否取消
     */
    @Builder.Default
    private volatile boolean cancelled;

    /**
     * 当前执行的节点 ID
     */
    private volatile String currentNodeId;

    /**
     * 是否启用调试模式
     */
    @Builder.Default
    private boolean debugMode;

    /**
     * 父上下文（用于迭代节点的子上下文）
     * @param key 参数键
     * @param value 参数值
     */
    private ExecutionContext parentContext;

    /**
     * 设置变量
     * @param key 参数键
     * @param value 参数值
     */
    public void setVariable(String key, Object value) {
        if (value == null) {
            variables.remove(key);
        } else {
            variables.put(key, value);
        }
    }

    /**
     * 获取变量
     * @param <T> 元素类型
     * @param key 参数键
     * @param type 类型
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    public <T> T getVariable(String key, Class<T> type) {
        Object value = variables.get(key);
        if (value == null) {
            return null;
        }
        if (type.isInstance(value)) {
            return (T) value;
        }
        // 处理数字类型转换
        if (type == Long.class && value instanceof Number) {
            return (T) Long.valueOf(((Number) value).longValue());
        }
        if (type == Integer.class && value instanceof Number) {
            return (T) Integer.valueOf(((Number) value).intValue());
        }
        if (type == Double.class && value instanceof Number) {
            return (T) Double.valueOf(((Number) value).doubleValue());
        }
        if (type == String.class) {
            return (T) String.valueOf(value);
        }
        return (T) value;
    }

    /**
     * 获取变量（带默认值）
     * @param <T> 元素类型
     * @param defaultValue defaultValue 参数
     * @param key 参数键
     * @param type 类型
     * @return 处理结果
     */
    public <T> T getVariable(String key, Class<T> type, T defaultValue) {
        T value = getVariable(key, type);
        return value != null ? value : defaultValue;
    }

    /**
     * 获取所有变量
     * @return 处理结果
     */
    public Map<String, Object> getAllVariables() {
        return new ConcurrentHashMap<>(variables);
    }

    // ==================== 节点输出管理方法 ====================

    /**
     * 设置节点输出
     * 存储节点的完整输出，用于变量引用解析
     *
     * @param nodeId  节点 ID
     * @param outputs 节点输出 Map
     */
    public void setNodeOutput(String nodeId, Map<String, Object> outputs) {
        if (nodeId == null || outputs == null) {
            return;
        }
        nodeOutputs.put(nodeId, new HashMap<>(outputs));
        log.debug("已设置节点 {} 的输出，键数量={}", nodeId, outputs.size());
    }

    /**
     * 设置节点输出（单个值）
     *
     * @param nodeId 节点 ID
     * @param key    输出键
     * @param value  输出值
     */
    public void setNodeOutput(String nodeId, String key, Object value) {
        if (nodeId == null || key == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> outputs = (Map<String, Object>) nodeOutputs.computeIfAbsent(
                nodeId, k -> new ConcurrentHashMap<>());
        outputs.put(key, value);
    }

    /**
     * 获取节点输出
     *
     * @param nodeId 节点 ID
     * @return 节点输出 Map，如果不存在返回 null
     */
    public Object getNodeOutput(String nodeId) {
        if (nodeId == null) {
            return null;
        }
        Object output = nodeOutputs.get(nodeId);
        // 如果当前上下文没有，尝试从父上下文获取
        if (output == null && parentContext != null) {
            output = parentContext.getNodeOutput(nodeId);
        }
        return output;
    }

    /**
     * 获取节点输出的特定键值
     *
     * @param nodeId 节点 ID
     * @param key    输出键
     * @param type   期望的类型
     * @param <T>    返回类型
     * @return 输出值，如果不存在返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T getNodeOutput(String nodeId, String key, Class<T> type) {
        Object outputs = getNodeOutput(nodeId);
        if (outputs instanceof Map) {
            Object value = ((Map<String, Object>) outputs).get(key);
            if (value == null) {
                return null;
            }
            if (type.isInstance(value)) {
                return (T) value;
            }
            // 类型转换
            if (type == String.class) {
                return (T) String.valueOf(value);
            }
        }
        return null;
    }

    /**
     * 获取所有节点输出
     *
     * @return 所有节点输出的副本
     */
    public Map<String, Object> getAllNodeOutputs() {
        Map<String, Object> allOutputs = new ConcurrentHashMap<>();
        // 先添加父上下文的输出
        if (parentContext != null) {
            allOutputs.putAll(parentContext.getAllNodeOutputs());
        }
        // 当前上下文的输出会覆盖父上下文
        allOutputs.putAll(nodeOutputs);
        return allOutputs;
    }

    /**
     * 检查节点是否有输出
     *
     * @param nodeId 节点 ID
     * @return 是否有输出
     */
    public boolean hasNodeOutput(String nodeId) {
        if (nodeOutputs.containsKey(nodeId)) {
            return true;
        }
        return parentContext != null && parentContext.hasNodeOutput(nodeId);
    }

    // ==================== 子上下文管理方法 ====================

    /**
     * 创建子上下文（用于迭代节点）
     * 子上下文继承父上下文的以下内容：
     * - executionId, workflowId, workflowVersion
     * - userId, tenantId
     * - sseEmitter
     * - debugMode
     * - 父上下文的 nodeOutputs（只读访问）
     * 子上下文独立的内容：
     * - variables（独立的变量空间）
     * - nodeOutputs（独立的节点输出空间）
     * - nodeStates（独立的节点状态）
     *
     * @return 新的子上下文
     */
    public ExecutionContext createChildContext() {
        return ExecutionContext.builder()
                .executionId(this.executionId)
                .workflowId(this.workflowId)
                .workflowVersion(this.workflowVersion)
                .userId(this.userId)
                .tenantId(this.tenantId)
                .permissions(this.permissions)
                .sseEmitter(this.sseEmitter)
                .debugMode(this.debugMode)
                .parentContext(this)
                .variables(new ConcurrentHashMap<>())
                .nodeOutputs(new ConcurrentHashMap<>())
                .nodeStates(new ConcurrentHashMap<>())
                .breakpoints(this.breakpoints)
                .build();
    }

    /**
     * 创建子上下文并设置迭代变量
     *
     * @param item  当前迭代元素
     * @param index 当前索引
     * @return 新的子上下文
     */
    public ExecutionContext createChildContext(Object item, int index) {
        ExecutionContext child = createChildContext();
        child.setVariable("item", item);
        child.setVariable("index", index);
        return child;
    }

    /**
     * 检查是否是子上下文
     *
     * @return 是否有父上下文
     */
    public boolean isChildContext() {
        return parentContext != null;
    }

    /**
     * 获取根上下文
     *
     * @return 根上下文（没有父上下文的最顶层上下文）
     */
    public ExecutionContext getRootContext() {
        if (parentContext == null) {
            return this;
        }
        return parentContext.getRootContext();
    }

    /**
     * 更新节点状态
     * @param nodeId 节点标识
     * @param status 状态
     */
    public void updateNodeState(String nodeId, ExecutionStatus status) {
        NodeExecutionState state = nodeStates.computeIfAbsent(nodeId, k -> new NodeExecutionState());
        state.setStatus(status);
    }

    /**
     * 更新节点状态（带输入输出）
     * @param input 输入数据
     * @param output output 参数
     * @param nodeId 节点标识
     * @param status 状态
     */
    public void updateNodeState(String nodeId, ExecutionStatus status,
                                Map<String, Object> input, Map<String, Object> output) {
        NodeExecutionState state = nodeStates.computeIfAbsent(nodeId, k -> {
            NodeExecutionState newState = new NodeExecutionState();
            // 设置执行顺序（从1开始）
            newState.setOrder(nodeStates.size() + 1);
            return newState;
        });
        state.setStatus(status);
        if (input != null) {
            state.setInput(input);
        }
        if (output != null) {
            state.setOutput(output);
        }
    }

    /**
     * 获取节点状态
     * @param nodeId 节点标识
     * @return 处理结果
     */
    public NodeExecutionState getNodeState(String nodeId) {
        return nodeStates.get(nodeId);
    }

    /**
     * 检查是否在断点处
     * @param nodeId 节点标识
     * @return 处理结果
     */
    public boolean isBreakpoint(String nodeId) {
        return breakpoints.contains(nodeId);
    }

    /**
     * 添加断点
     * @param nodeId 节点标识
     */
    public void addBreakpoint(String nodeId) {
        breakpoints.add(nodeId);
    }

    /**
     * 移除断点
     * @param nodeId 节点标识
     */
    public void removeBreakpoint(String nodeId) {
        breakpoints.remove(nodeId);
    }

    /**
     * 等待如果暂停
     * @throws InterruptedException 处理失败时抛出
     */
    public void waitIfPaused() throws InterruptedException {
        while (paused && !cancelled) {
            Thread.sleep(100);
        }
    }

    /**
     * 检查是否应该停止执行
     * @return 处理结果
     */
    public boolean shouldStop() {
        return cancelled;
    }

    /**
     * 节点执行状态
     */
    @Data
    public static class NodeExecutionState {

        /** 执行顺序（从1开始） */
        private Integer order;
        private ExecutionStatus status;
        private Map<String, Object> input;
        private Map<String, Object> output;
        private String error;
        private Long duration;
        private String streamingContent;
    }

    // ==================== SSE 事件推送方法 ====================

    /**
     * 推送 SSE 事件
     *
     * @param eventName 事件名称
     * @param data      事件数据
     */
    public void pushEvent(String eventName, Object data) {
        if (sseEmitter == null) {
            log.debug("SSE emitter 为空，跳过事件: {}", eventName);
            return;
        }

        try {
            String jsonData;
            if (data instanceof String) {
                jsonData = (String) data;
            } else {
                jsonData = OBJECT_MAPPER.writeValueAsString(data);
            }

            sseEmitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(jsonData));

            log.debug("已推送 SSE 事件: {}，数据: {}", eventName, jsonData);
        } catch (IOException e) {
            log.warn("推送 SSE 事件 {} 失败: {}", eventName, e.getMessage());
            // 不抛出异常，避免影响工作流执行
        }
    }

    /**
     * 推送执行开始事件
     * @param inputs 输入参数
     */
    public void pushExecutionStarted(Map<String, Object> inputs) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("workflowId", workflowId);
        eventData.put("inputs", inputs);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_STARTED.code(), eventData);
    }

    /**
     * 推送执行完成事件
     * @param duration 持续时间
     * @param outputs 输出数据
     */
    public void pushExecutionCompleted(Map<String, Object> outputs, long duration) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("outputs", outputs);
        eventData.put("duration", duration);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_COMPLETED.code(), eventData);
    }

    /**
     * 推送执行失败事件
     * @param errorMessage 错误信息
     */
    public void pushExecutionFailed(String errorMessage) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        // ConcurrentHashMap 不允许 null 值
        eventData.put("error", errorMessage != null ? errorMessage : "Unknown error");
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_FAILED.code(), eventData);
    }

    /**
     * 推送执行恢复事件
     */
    public void pushExecutionResumed() {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_RESUMED.code(), eventData);
    }

    /**
     * 推送执行取消事件
     * @param reason reason 参数
     */
    public void pushExecutionCancelled(String reason) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("reason", reason != null ? reason : "用户取消执行");
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_CANCELLED.code(), eventData);
    }

    /**
     * 推送节点开始事件
     * @param input 输入数据
     * @param nodeId 节点标识
     */
    public void pushNodeStarted(String nodeId, Map<String, Object> input) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("nodeId", nodeId);
        eventData.put("input", input);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.NODE_STARTED.code(), eventData);
    }

    /**
     * 推送节点完成事件
     * @param duration 持续时间
     * @param nodeId 节点标识
     * @param output output 参数
     */
    public void pushNodeCompleted(String nodeId, Map<String, Object> output, Long duration) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("nodeId", nodeId);
        // ConcurrentHashMap 不允许 null 值，需要处理 null 情况
        if (output != null) {
            eventData.put("output", output);
        } else {
            eventData.put("output", Map.of());
        }
        if (duration != null) {
            eventData.put("duration", duration);
        }
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.NODE_COMPLETED.code(), eventData);
    }

    /**
     * 推送节点失败事件
     * @param errorMessage 错误信息
     * @param nodeId 节点标识
     */
    public void pushNodeFailed(String nodeId, String errorMessage) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("nodeId", nodeId);
        eventData.put("error", errorMessage);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.NODE_FAILED.code(), eventData);
    }

    /**
     * 推送流式 Token 事件（用于 LLM 节点）
     * @param nodeId 节点标识
     * @param token token 参数
     */
    public void pushStreamToken(String nodeId, String token) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("nodeId", nodeId);
        eventData.put("token", token);
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.NODE_DELTA.code(), eventData);

        // 同时更新节点状态中的流式内容
        NodeExecutionState state = nodeStates.get(nodeId);
        if (state != null) {
            String existing = state.getStreamingContent();
            state.setStreamingContent(existing == null ? token : existing + token);
        }
    }

    /**
     * 推送断点命中事件
     * @param nodeId 节点标识
     */
    public void pushBreakpointHit(String nodeId) {
        Map<String, Object> eventData = new ConcurrentHashMap<>();
        eventData.put("executionId", executionId);
        eventData.put("nodeId", nodeId);
        eventData.put("variables", getAllVariables());
        eventData.put("timestamp", System.currentTimeMillis());
        pushEvent(WorkflowRuntimeEventType.WORKFLOW_PAUSED.code(), eventData);
    }

    /**
     * 完成 SSE 连接
     */
    public void completeSse() {
        if (sseEmitter != null) {
            try {
                sseEmitter.complete();
            } catch (Exception e) {
                log.warn("完成 SSE emitter 失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 关闭 SSE 连接（带错误）
     * @param error error 参数
     */
    public void completeSseWithError(Throwable error) {
        if (sseEmitter != null) {
            try {
                sseEmitter.completeWithError(error);
            } catch (Exception e) {
                log.warn("以异常状态完成 SSE emitter 失败: {}", e.getMessage());
            }
        }
    }
}
