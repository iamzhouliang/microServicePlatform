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
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 组合工作流执行监听器
 * 支持注册多个监听器，按顺序调用
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
public class CompositeWorkflowExecutionListener implements WorkflowExecutionListener {

    private final List<WorkflowExecutionListener> listeners = new ArrayList<>();

    /**
     * 添加监听器
     *
     * @param listener 监听器
     */
    public void addListener(WorkflowExecutionListener listener) {
        if (listener != null && listener != this) {
            listeners.add(listener);
        }
    }

    /**
     * 移除监听器
     *
     * @param listener 监听器
     */
    public void removeListener(WorkflowExecutionListener listener) {
        listeners.remove(listener);
    }

    /**
     * 清空所有监听器
     */
    public void clearListeners() {
        listeners.clear();
    }

    @Override
    public void onExecutionStarted(ExecutionContext context, Map<String, Object> inputs) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionStarted(context, inputs);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionStarted 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onExecutionCompleted(ExecutionContext context, Map<String, Object> outputs, long duration) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionCompleted(context, outputs, duration);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionCompleted 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onExecutionFailed(ExecutionContext context, String errorMessage) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionFailed(context, errorMessage);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionFailed 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onExecutionPaused(ExecutionContext context, String currentNodeId) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionPaused(context, currentNodeId);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionPaused 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onExecutionResumed(ExecutionContext context) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionResumed(context);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionResumed 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onExecutionCancelled(ExecutionContext context) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onExecutionCancelled(context);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onExecutionCancelled 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onNodeStarted(ExecutionContext context, String nodeId, Map<String, Object> input) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onNodeStarted(context, nodeId, input);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onNodeStarted 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onNodeCompleted(ExecutionContext context, String nodeId, Map<String, Object> output, Long duration) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onNodeCompleted(context, nodeId, output, duration);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onNodeCompleted 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onNodeFailed(ExecutionContext context, String nodeId, String errorMessage) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onNodeFailed(context, nodeId, errorMessage);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onNodeFailed 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onStreamToken(ExecutionContext context, String nodeId, String token) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onStreamToken(context, nodeId, token);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onStreamToken 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onBreakpointHit(ExecutionContext context, String nodeId) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onBreakpointHit(context, nodeId);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onBreakpointHit 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }

    @Override
    public void onVariableUpdated(ExecutionContext context, String variableName, Object value) {
        for (WorkflowExecutionListener listener : listeners) {
            try {
                listener.onVariableUpdated(context, variableName, value);
            } catch (Exception e) {
                log.warn("监听器 {} 处理 onVariableUpdated 失败: {}",
                        listener.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
