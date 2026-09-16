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

package com.microservice.platform.ai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.ExecutionStatus;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionResult;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionSnapshot;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowCompiler;
import com.microservice.platform.ai.core.workflow.runtime.CompiledWorkflow;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowRuntimeExecutor;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.definition.WorkflowRuntimeEventType;
import com.microservice.platform.ai.core.workflow.listener.WorkflowExecutionListener;
import com.microservice.platform.ai.domain.dto.req.WorkflowExecutionPageReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowExecutionResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowDefinition;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowExecution;
import com.microservice.platform.ai.repository.workflow.WorkflowDefinitionMapper;
import com.microservice.platform.ai.repository.workflow.WorkflowExecutionMapper;
import com.microservice.platform.ai.service.WorkflowExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流执行服务实现类
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutionServiceImpl extends SuperServiceImpl<WorkflowExecutionMapper, WorkflowExecution>
        implements
            WorkflowExecutionService {

    private static final int AGENTIC_SNAPSHOT_SCHEMA_VERSION = 1;
    private static final String AGENTIC_SNAPSHOT_RUNTIME_TYPE = "AGENTIC_LANGCHAIN4J";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * SSE 超时时间（30分钟）
     */
    private static final long SSE_TIMEOUT = 30 * 60 * 1000L;

    private final AuthenticationContext context;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final WorkflowCompiler workflowCompiler;
    private final WorkflowRuntimeExecutor workflowRuntimeExecutor;
    private final WorkflowExecutionListener executionListener;

    /**
     * 自注入代理，用于解决 @Async 内部调用不生效的问题
     */
    @Lazy
    @Autowired
    private WorkflowExecutionServiceImpl self;

    /**
     * SSE 发射器缓存
     */
    private final Map<String, SseEmitter> sseEmitters = new ConcurrentHashMap<>();

    /**
     * 执行上下文缓存
     */
    private final Map<String, ExecutionContext> executionContexts = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResp execute(Long workflowId, Map<String, Object> inputs) {
        return execute(workflowId, inputs, Collections.emptySet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResp execute(Long workflowId, Map<String, Object> inputs, Set<String> breakpoints) {
        // 获取工作流定义
        WorkflowDefinition workflow = getWorkflowDefinition(workflowId);

        // 创建执行记录
        WorkflowExecution execution = createExecution(workflow, inputs);

        // 创建执行上下文
        ExecutionContext executionContext = createExecutionContext(execution, breakpoints, null);
        executionContexts.put(execution.getExecutionId(), executionContext);

        try {
            // 执行 LangChain4j 智能体工作流
            executionListener.onExecutionStarted(executionContext, inputs == null ? Map.of() : inputs);
            WorkflowExecutionResult result = executeWorkflowRuntime(workflow, inputs, executionContext);

            // 更新执行记录
            updateExecutionFromWorkflowResult(execution, result);
            notifyExecutionFinished(executionContext, result, execution.getDuration());

            return convertToResp(execution, workflow.getName(), result.executedNodeIds(), null);

        } catch (Exception e) {
            log.error("工作流执行失败: {}", e.getMessage(), e);
            updateExecutionFailed(execution, e.getMessage());
            throw CheckedException.badRequest("工作流执行失败: " + e.getMessage());
        } finally {
            executionContexts.remove(execution.getExecutionId());
        }
    }

    @Override
    public String executeAsync(Long workflowId, Map<String, Object> inputs) {
        return executeAsync(workflowId, inputs, Collections.emptySet());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String executeAsync(Long workflowId, Map<String, Object> inputs, Set<String> breakpoints) {
        // 获取工作流定义
        WorkflowDefinition workflow = getWorkflowDefinition(workflowId);

        // 创建执行记录
        WorkflowExecution execution = createExecution(workflow, inputs);

        // 异步执行（通过 self 代理调用，确保 @Async 注解生效）
        self.executeWorkflowAsync(workflow, execution, inputs, breakpoints);

        return execution.getExecutionId();
    }

    /**
     * 异步执行工作流
     * @param breakpoints breakpoints 参数
     * @param inputs 输入参数
     * @param execution 执行记录
     * @param workflow 工作流
     */
    @Async("workflowExecutor")
    protected void executeWorkflowAsync(WorkflowDefinition workflow, WorkflowExecution execution,
                                        Map<String, Object> inputs, Set<String> breakpoints) {
        String executionId = execution.getExecutionId();

        // 等待 SSE 连接建立（最多等 3 秒），解决竞态条件问题
        SseEmitter sseEmitter = waitForSseEmitter(executionId, 3000);

        // 创建执行上下文
        ExecutionContext executionContext = createExecutionContext(execution, breakpoints, sseEmitter);
        executionContexts.put(executionId, executionContext);

        try {
            // 更新状态为运行中
            updateExecutionStatus(execution, ExecutionStatus.RUNNING);

            // 执行 LangChain4j 智能体工作流
            executionListener.onExecutionStarted(executionContext, inputs == null ? Map.of() : inputs);
            WorkflowExecutionResult result = executeWorkflowRuntime(workflow, inputs, executionContext);

            // 更新执行记录
            updateExecutionFromWorkflowResult(execution, result);
            notifyExecutionFinished(executionContext, result, execution.getDuration());

        } catch (Exception e) {
            log.error("异步工作流执行失败: {}", e.getMessage(), e);
            updateExecutionFailed(execution, e.getMessage());
            executionListener.onExecutionFailed(executionContext, e.getMessage());
        } finally {
            executionContexts.remove(executionId);
            sseEmitters.remove(executionId);
        }
    }

    @Override
    public void pause(String executionId) {
        ExecutionContext executionContext = getExecutionContext(executionId);
        executionContext.setPaused(true);

        // 更新数据库状态
        WorkflowExecution execution = getExecutionByExecutionId(executionId);
        updateExecutionStatus(execution, ExecutionStatus.PAUSED);

        // 通知监听器
        executionListener.onExecutionPaused(executionContext, executionContext.getCurrentNodeId());

        log.info("工作流执行已暂停: {}", executionId);
    }

    @Override
    public void resume(String executionId) {
        ExecutionContext executionContext = getExecutionContext(executionId);
        executionContext.setPaused(false);

        // 更新数据库状态
        WorkflowExecution execution = getExecutionByExecutionId(executionId);
        updateExecutionStatus(execution, ExecutionStatus.RUNNING);

        // 通知监听器
        executionListener.onExecutionResumed(executionContext);

        log.info("工作流执行已恢复: {}", executionId);
    }

    @Override
    public void cancel(String executionId) {
        ExecutionContext executionContext = executionContexts.get(executionId);
        if (executionContext != null) {
            executionContext.setCancelled(true);
            executionListener.onExecutionCancelled(executionContext);
        }

        // 更新数据库状态
        WorkflowExecution execution = getExecutionByExecutionId(executionId);
        updateExecutionStatus(execution, ExecutionStatus.CANCELLED);

        // 清理资源
        executionContexts.remove(executionId);
        SseEmitter emitter = sseEmitters.remove(executionId);
        if (emitter != null) {
            emitter.complete();
        }

        log.info("工作流执行已取消: {}", executionId);
    }

    @Override
    public WorkflowExecutionResp getExecution(String executionId) {
        WorkflowExecution execution = getExecutionByExecutionId(executionId);
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(execution.getWorkflowId());
        String workflowName = workflow != null ? workflow.getName() : null;

        // 获取执行上下文中的当前节点
        ExecutionContext executionContext = executionContexts.get(executionId);
        String currentNodeId = executionContext != null ? executionContext.getCurrentNodeId() : null;

        return convertToResp(execution, workflowName, null, currentNodeId);
    }

    @Override
    public IPage<WorkflowExecutionResp> pageExecutions(WorkflowExecutionPageReq req) {
        // 分页查询时排除大字段（snapshot, node_states, inputs, outputs），避免内存溢出
        return baseMapper.selectPage(req.buildPage(), Wraps.<WorkflowExecution>lbQ()
                .select(WorkflowExecution.class, info -> !info.getColumn().equals("snapshot")
                        && !info.getColumn().equals("node_states")
                        && !info.getColumn().equals("inputs")
                        && !info.getColumn().equals("outputs"))
                .eq(req.getWorkflowId() != null, WorkflowExecution::getWorkflowId, req.getWorkflowId())
                .eq(StringUtils.hasText(req.getStatus()), WorkflowExecution::getStatus, req.getStatus())
                .ge(req.getStartTimeFrom() != null, WorkflowExecution::getStartTime, req.getStartTimeFrom())
                .le(req.getStartTimeTo() != null, WorkflowExecution::getStartTime, req.getStartTimeTo())
                .eq(WorkflowExecution::getUserId, context.userId())
                .orderByDesc(WorkflowExecution::getStartTime))
                .convert(e -> {
                    WorkflowDefinition workflow = workflowDefinitionMapper.selectById(e.getWorkflowId());
                    String workflowName = workflow != null ? workflow.getName() : null;
                    return convertToResp(e, workflowName, null, null);
                });
    }

    @Override
    public SseEmitter subscribe(String executionId) {
        // 检查执行是否存在
        WorkflowExecution execution = getExecutionByExecutionId(executionId);

        // 创建 SSE 发射器
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT);

        // 设置回调
        emitter.onCompletion(() -> {
            log.debug("SSE 连接已完成，执行ID: {}", executionId);
            sseEmitters.remove(executionId);
        });

        emitter.onTimeout(() -> {
            log.debug("SSE 连接超时，执行ID: {}", executionId);
            sseEmitters.remove(executionId);
        });

        emitter.onError(e -> {
            log.warn("SSE 连接异常，执行ID: {}", executionId, e);
            sseEmitters.remove(executionId);
        });

        // 缓存发射器
        sseEmitters.put(executionId, emitter);

        // 如果执行上下文已存在，更新其 SSE 发射器
        ExecutionContext executionContext = executionContexts.get(executionId);
        if (executionContext != null) {
            executionContext.setSseEmitter(emitter);
        } else {
            replayTerminalEventIfNecessary(execution, emitter);
        }

        log.info("SSE 订阅已创建，执行ID: {}", executionId);
        return emitter;
    }

    static boolean isTerminalStatus(String status) {
        return Objects.equals(ExecutionStatus.COMPLETED.getCode(), status)
                || Objects.equals(ExecutionStatus.FAILED.getCode(), status)
                || Objects.equals(ExecutionStatus.CANCELLED.getCode(), status);
    }

    private void replayTerminalEventIfNecessary(WorkflowExecution execution, SseEmitter emitter) {
        if (execution == null || !isTerminalStatus(execution.getStatus())) {
            return;
        }
        try {
            if (Objects.equals(ExecutionStatus.COMPLETED.getCode(), execution.getStatus())) {
                sendSseEvent(emitter, WorkflowRuntimeEventType.WORKFLOW_COMPLETED.code(), Map.of(
                        "executionId", execution.getExecutionId(),
                        "outputs", execution.getOutputs() == null ? Map.of() : execution.getOutputs(),
                        "duration", execution.getDuration() == null ? 0L : execution.getDuration(),
                        "timestamp", System.currentTimeMillis()));
            } else if (Objects.equals(ExecutionStatus.CANCELLED.getCode(), execution.getStatus())) {
                sendSseEvent(emitter, WorkflowRuntimeEventType.WORKFLOW_CANCELLED.code(), Map.of(
                        "executionId", execution.getExecutionId(),
                        "reason", "工作流执行已取消",
                        "timestamp", System.currentTimeMillis()));
            } else {
                sendSseEvent(emitter, WorkflowRuntimeEventType.WORKFLOW_FAILED.code(), Map.of(
                        "executionId", execution.getExecutionId(),
                        "error", Objects.toString(execution.getErrorMessage(), "工作流执行失败"),
                        "timestamp", System.currentTimeMillis()));
            }
        } catch (IOException e) {
            log.warn("补发 SSE 终态事件失败，执行ID: {}, 错误: {}", execution.getExecutionId(), e.getMessage());
        } finally {
            sseEmitters.remove(execution.getExecutionId());
            emitter.complete();
        }
    }

    private void sendSseEvent(SseEmitter emitter, String eventName, Map<String, Object> data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(OBJECT_MAPPER.writeValueAsString(data)));
    }

    @Override
    public void updateVariable(String executionId, String variableName, Object value) {
        ExecutionContext executionContext = getExecutionContext(executionId);
        executionContext.setVariable(variableName, value);

        // 通知监听器
        executionListener.onVariableUpdated(executionContext, variableName, value);

        log.debug("执行变量已更新，执行ID: {}, {} = {}", executionId, variableName, value);
    }

    @Override
    public Map<String, Object> getSnapshot(String executionId) {
        WorkflowExecution execution = getExecutionByExecutionId(executionId);

        // 优先从执行上下文获取
        ExecutionContext executionContext = executionContexts.get(executionId);
        if (executionContext != null) {
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("variables", executionContext.getAllVariables());
            snapshot.put("currentNodeId", executionContext.getCurrentNodeId());
            snapshot.put("nodeStates", executionContext.getNodeStates());
            snapshot.put("breakpoints", executionContext.getBreakpoints());
            return snapshot;
        }

        // 从数据库获取
        return execution.getSnapshot();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowExecutionResp resumeFromSnapshot(String executionId, Map<String, Object> snapshot) {
        WorkflowExecution execution = getExecutionByExecutionId(executionId);

        if (!ExecutionStatus.PAUSED.getCode().equals(execution.getStatus())) {
            throw CheckedException.badRequest("只能从暂停状态恢复执行");
        }

        // 获取工作流定义
        WorkflowDefinition workflow = getWorkflowDefinition(execution.getWorkflowId());

        // 创建执行上下文并恢复快照
        ExecutionContext executionContext = createExecutionContext(execution, Collections.emptySet(),
                sseEmitters.get(executionId));

        // 恢复当前节点
        if (snapshot != null && snapshot.containsKey("currentNodeId")) {
            executionContext.setCurrentNodeId((String) snapshot.get("currentNodeId"));
        }

        executionContexts.put(executionId, executionContext);

        try {
            // 恢复执行
            executionContext.setPaused(false);
            WorkflowExecutionResult result = resumeWorkflowRuntime(workflow, readWorkflowSnapshot(snapshot), executionContext);

            // 更新执行记录
            updateExecutionFromWorkflowResult(execution, result);
            notifyExecutionFinished(executionContext, result, execution.getDuration());

            return convertToResp(execution, workflow.getName(), result.executedNodeIds(), null);

        } catch (Exception e) {
            log.error("从快照恢复执行失败: {}", e.getMessage(), e);
            updateExecutionFailed(execution, e.getMessage());
            executionListener.onExecutionFailed(executionContext, e.getMessage());
            throw CheckedException.badRequest("恢复执行失败: " + e.getMessage());
        } finally {
            executionContexts.remove(executionId);
        }
    }

    // ==================== 私有方法 ====================

    /**
     * 等待 SSE 发射器建立连接
     * 解决竞态条件：前端调用 executeAsync 后立即调用 subscribe，
     * 但 @Async 方法可能在 subscribe 之前就开始执行了。
     * 通过等待一小段时间，确保 SSE 连接有机会建立。
     *
     * @param executionId 执行ID
     * @param timeoutMs   超时时间（毫秒）
     * @return SSE 发射器，如果超时则返回 null
     */
    private SseEmitter waitForSseEmitter(String executionId, long timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            SseEmitter emitter = sseEmitters.get(executionId);
            if (emitter != null) {
                log.debug("已找到 SSE 发射器，执行ID: {}, 等待: {}ms",
                        executionId, System.currentTimeMillis() - start);
                return emitter;
            }
            try {
                // 每 50ms 检查一次
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        log.warn("等待 {}ms 后仍未找到 SSE 发射器，执行ID: {}，将不带 SSE 推送继续执行",
                timeoutMs, executionId);
        // 超时后继续执行，只是没有 SSE 推送
        return null;
    }

    /**
     * 获取工作流定义
     * @param workflowId 工作流标识
     * @return 处理结果
     */
    private WorkflowDefinition getWorkflowDefinition(Long workflowId) {
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(workflowId);
        if (workflow == null) {
            throw CheckedException.notFound("工作流不存在");
        }
        if (workflow.getGraph() == null) {
            throw CheckedException.badRequest("工作流图定义为空");
        }
        return workflow;
    }

    /**
     * 根据执行ID获取执行记录
     * @param executionId 执行标识
     * @return 处理结果
     */
    private WorkflowExecution getExecutionByExecutionId(String executionId) {
        WorkflowExecution execution = baseMapper.selectByExecutionId(executionId);
        if (execution == null) {
            throw CheckedException.notFound("执行记录不存在");
        }
        return execution;
    }

    /**
     * 获取执行上下文
     * @param executionId 执行标识
     * @return 处理结果
     */
    private ExecutionContext getExecutionContext(String executionId) {
        ExecutionContext executionContext = executionContexts.get(executionId);
        if (executionContext == null) {
            throw CheckedException.badRequest("执行上下文不存在，可能执行已结束");
        }
        return executionContext;
    }

    /**
     * 创建执行记录
     * @param inputs 输入参数
     * @param workflow 工作流
     * @return 处理结果
     */
    private WorkflowExecution createExecution(WorkflowDefinition workflow, Map<String, Object> inputs) {
        WorkflowExecution execution = WorkflowExecution.builder()
                .executionId(UUID.randomUUID().toString())
                .workflowId(workflow.getId())
                .workflowVersion(workflow.getCurrentVersion())
                .status(ExecutionStatus.PENDING.getCode())
                .inputs(inputs)
                .startTime(Instant.now())
                .userId(context.userId())
                .tenantId(context.tenantId())
                .build();

        baseMapper.insert(execution);
        log.info("已创建工作流执行记录: {}", execution.getExecutionId());
        return execution;
    }

    /**
     * 创建执行上下文
     * @param execution 执行记录
     * @param sseEmitter sseEmitter 参数
     * @param breakpoints breakpoints 参数
     * @return 处理结果
     */
    private ExecutionContext createExecutionContext(WorkflowExecution execution, Set<String> breakpoints,
                                                    SseEmitter sseEmitter) {
        return ExecutionContext.builder()
                .executionId(execution.getExecutionId())
                .workflowId(execution.getWorkflowId())
                .workflowVersion(execution.getWorkflowVersion())
                .userId(execution.getUserId())
                .tenantId(execution.getTenantId())
                .permissions(context.funcPermissionList() == null
                        ? Set.of()
                        : Set.copyOf(context.funcPermissionList()))
                .breakpoints(copyBreakpoints(breakpoints))
                .sseEmitter(sseEmitter)
                .debugMode(breakpoints != null && !breakpoints.isEmpty())
                .build();
    }

    /**
     * 更新执行状态
     * @param execution 执行记录
     * @param status 状态
     */
    private void updateExecutionStatus(WorkflowExecution execution, ExecutionStatus status) {
        execution.setStatus(status.getCode());
        baseMapper.updateById(execution);
    }

    /**
     * 编译并执行智能体工作流。
     * @param executionContext executionContext 参数
     * @param inputs 输入参数
     * @param workflow 工作流
     * @return 处理结果
     */
    private WorkflowExecutionResult executeWorkflowRuntime(WorkflowDefinition workflow, Map<String, Object> inputs,
                                                           ExecutionContext executionContext) {
        CompiledWorkflow compiled = workflowCompiler.compile(workflow.getGraph());
        return workflowRuntimeExecutor.execute(compiled, inputs, executionContext);
    }

    private WorkflowExecutionResult resumeWorkflowRuntime(WorkflowDefinition workflow, WorkflowExecutionSnapshot snapshot,
                                                          ExecutionContext executionContext) {
        CompiledWorkflow compiled = workflowCompiler.compile(workflow.getGraph());
        return workflowRuntimeExecutor.resume(compiled, snapshot, executionContext);
    }

    private Set<String> copyBreakpoints(Set<String> breakpoints) {
        Set<String> copied = ConcurrentHashMap.newKeySet();
        if (breakpoints != null) {
            copied.addAll(breakpoints);
        }
        return copied;
    }

    /**
     * 根据 Workflow 执行结果更新执行记录。
     * @param result 处理结果
     * @param execution 执行记录
     */
    private void updateExecutionFromWorkflowResult(WorkflowExecution execution, WorkflowExecutionResult result) {
        Instant endTime = Instant.now();
        execution.setStatus(result.success() ? ExecutionStatus.COMPLETED.getCode() : ExecutionStatus.FAILED.getCode());
        execution.setOutputs(result.outputs());
        execution.setErrorMessage(result.errorMessage());
        execution.setEndTime(endTime);
        if (execution.getStartTime() != null) {
            execution.setDuration(endTime.toEpochMilli() - execution.getStartTime().toEpochMilli());
        }
        execution.setNodeStates(toWorkflowNodeStates(result));
        execution.setSnapshot(writeWorkflowSnapshot(result.snapshot()));
        applyTokenUsage(execution, result);
        baseMapper.updateById(execution);
    }

    static void applyTokenUsage(WorkflowExecution execution, WorkflowExecutionResult result) {
        long inputTokens = 0L;
        long outputTokens = 0L;
        Set<String> llmNodes = new HashSet<>();

        for (Map.Entry<String, Object> entry : result.scope().state().entrySet()) {
            String key = entry.getKey();
            if (!key.startsWith("nodes.") || !(entry.getValue()instanceof Number tokenCount)) {
                continue;
            }
            if (key.endsWith(".inputTokens")) {
                inputTokens += Math.max(0L, tokenCount.longValue());
            } else if (key.endsWith(".outputTokens")) {
                outputTokens += Math.max(0L, tokenCount.longValue());
            } else {
                continue;
            }
            llmNodes.add(key.substring(0, key.lastIndexOf('.')));
        }

        execution.setInputTokens(inputTokens);
        execution.setOutputTokens(outputTokens);
        execution.setTotalTokens(inputTokens + outputTokens);
        execution.setLlmCallCount(llmNodes.size());
    }

    private void notifyExecutionFinished(ExecutionContext context, WorkflowExecutionResult result, Long duration) {
        long safeDuration = duration == null ? 0L : duration;
        if (result.success()) {
            executionListener.onExecutionCompleted(context, result.outputs(), safeDuration);
        } else {
            executionListener.onExecutionFailed(context, result.errorMessage());
        }
    }

    private Map<String, Object> writeWorkflowSnapshot(WorkflowExecutionSnapshot snapshot) {
        if (snapshot == null) {
            return Map.of();
        }
        return Map.of(
                "schemaVersion", AGENTIC_SNAPSHOT_SCHEMA_VERSION,
                "runtimeType", AGENTIC_SNAPSHOT_RUNTIME_TYPE,
                "scope", snapshot.scope(),
                "pendingNodeIds", snapshot.pendingNodeIds(),
                "executedNodeIds", snapshot.executedNodeIds(),
                "activatedIncomingSources", snapshot.activatedIncomingSources());
    }

    @SuppressWarnings("unchecked")
    private WorkflowExecutionSnapshot readWorkflowSnapshot(Map<String, Object> snapshot) {
        Map<String, Object> source = snapshot == null ? Map.of() : snapshot;
        Map<String, Object> scope = source.get("scope")instanceof Map<?, ?> map
                ? map.entrySet().stream().collect(LinkedHashMap::new,
                        (target, entry) -> target.put(String.valueOf(entry.getKey()), entry.getValue()), LinkedHashMap::putAll)
                : Map.of();
        List<String> pending = source.get("pendingNodeIds")instanceof Collection<?> collection
                ? collection.stream().map(String::valueOf).toList()
                : List.of();
        Set<String> executed = source.get("executedNodeIds")instanceof Collection<?> collection
                ? collection.stream().map(String::valueOf).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new))
                : Set.of();
        Map<String, Set<String>> activated = new LinkedHashMap<>();
        if (source.get("activatedIncomingSources")instanceof Map<?, ?> activatedMap) {
            activatedMap.forEach((key, value) -> {
                if (value instanceof Collection<?> collection) {
                    activated.put(String.valueOf(key), collection.stream().map(String::valueOf)
                            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)));
                }
            });
        }
        return new WorkflowExecutionSnapshot(scope, pending, executed, activated);
    }

    /**
     * 将智能体作用域投影为前端历史和调试面板可读的节点状态。
     * @param result 处理结果
     * @return 处理结果
     */
    private Map<String, Object> toWorkflowNodeStates(WorkflowExecutionResult result) {
        Map<String, Object> states = new LinkedHashMap<>();
        Map<String, Object> scope = result.scope().state();
        List<String> executedNodeIds = result.executedNodeIds();
        for (int i = 0; i < executedNodeIds.size(); i++) {
            String nodeId = executedNodeIds.get(i);
            String prefix = "nodes." + nodeId + ".";
            Map<String, Object> output = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : scope.entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    output.put(entry.getKey().substring(prefix.length()), entry.getValue());
                }
            }
            Map<String, Object> state = new LinkedHashMap<>();
            state.put("order", i + 1);
            state.put("status", (!result.success() && i == executedNodeIds.size() - 1) ? "FAILED" : "COMPLETED");
            state.put("output", output);
            if (!result.success() && i == executedNodeIds.size() - 1) {
                state.put("error", result.errorMessage());
            }
            states.put(nodeId, state);
        }
        return states;
    }

    /**
     * 更新执行失败
     * @param execution 执行记录
     * @param errorMessage 错误信息
     */
    private void updateExecutionFailed(WorkflowExecution execution, String errorMessage) {
        execution.setStatus(ExecutionStatus.FAILED.getCode());
        execution.setErrorMessage(errorMessage);
        execution.setEndTime(Instant.now());
        if (execution.getStartTime() != null) {
            execution.setDuration(Instant.now().toEpochMilli() - execution.getStartTime().toEpochMilli());
        }
        baseMapper.updateById(execution);
    }

    /**
     * 转换为响应对象
     * @param currentNodeId currentNodeId 参数
     * @param executedNodes executedNodes 参数
     * @param execution 执行记录
     * @param workflowName workflowName 参数
     * @return 处理结果
     */
    private WorkflowExecutionResp convertToResp(WorkflowExecution execution, String workflowName,
                                                List<String> executedNodes, String currentNodeId) {
        WorkflowExecutionResp resp = WorkflowExecutionResp.builder()
                .id(execution.getId())
                .executionId(execution.getExecutionId())
                .workflowId(execution.getWorkflowId())
                .workflowName(workflowName)
                .workflowVersion(execution.getWorkflowVersion())
                .status(execution.getStatus())
                .inputs(execution.getInputs())
                .outputs(execution.getOutputs())
                .nodeStates(execution.getNodeStates())
                .errorMessage(execution.getErrorMessage())
                .startTime(execution.getStartTime())
                .endTime(execution.getEndTime())
                .duration(execution.getDuration())
                .inputTokens(execution.getInputTokens())
                .outputTokens(execution.getOutputTokens())
                .totalTokens(execution.getTotalTokens())
                .llmCallCount(execution.getLlmCallCount())
                .userId(execution.getUserId())
                .executedNodes(executedNodes)
                .currentNodeId(currentNodeId)
                .createdTime(execution.getCreateTime())
                .build();
        return resp;
    }

}
