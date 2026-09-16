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

package com.microservice.platform.ai.core.workflow.error;

import com.microservice.platform.ai.core.enums.AiEnum;
import lombok.Getter;

/**
 * 工作流执行异常
 *
 * @author xJh
 * @since 2026/01/07
 */
@Getter
public class WorkflowExecutionException extends RuntimeException {

    /**
     * 执行 ID
     */
    private final String executionId;

    /**
     * 节点 ID（如果是节点级别的错误）
     */
    private final String nodeId;

    /**
     * 错误类型
     */
    private final ErrorType errorType;

    /**
     * 是否可恢复
     * @param cause cause 参数
     * @param message 消息内容
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @return 处理结果
     */
    private final boolean recoverable;

    public WorkflowExecutionException(String message) {
        super(message);
        this.executionId = null;
        this.nodeId = null;
        this.errorType = ErrorType.UNKNOWN;
        this.recoverable = false;
    }

    public WorkflowExecutionException(String message, Throwable cause) {
        super(message, cause);
        this.executionId = null;
        this.nodeId = null;
        this.errorType = ErrorType.UNKNOWN;
        this.recoverable = false;
    }

    public WorkflowExecutionException(String executionId, String nodeId, String message,
                                      ErrorType errorType, boolean recoverable) {
        super(message);
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.errorType = errorType;
        this.recoverable = recoverable;
    }

    public WorkflowExecutionException(String executionId, String nodeId, String message,
                                      Throwable cause, ErrorType errorType, boolean recoverable) {
        super(message, cause);
        this.executionId = executionId;
        this.nodeId = nodeId;
        this.errorType = errorType;
        this.recoverable = recoverable;
    }

    /**
     * 创建节点执行异常
     * @param message 消息内容
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param cause cause 参数
     * @return 处理结果
     */
    public static WorkflowExecutionException nodeExecutionError(String executionId, String nodeId,
                                                                String message, Throwable cause) {
        return new WorkflowExecutionException(executionId, nodeId, message, cause,
                ErrorType.NODE_EXECUTION_ERROR, true);
    }

    /**
     * 创建节点配置异常
     * @param executionId 执行标识
     * @param message 消息内容
     * @param nodeId 节点标识
     * @return 处理结果
     */
    public static WorkflowExecutionException nodeConfigError(String executionId, String nodeId,
                                                             String message) {
        return new WorkflowExecutionException(executionId, nodeId, message,
                ErrorType.NODE_CONFIG_ERROR, false);
    }

    /**
     * 创建工作流结构异常
     * @param message 消息内容
     * @param executionId 执行标识
     * @return 处理结果
     */
    public static WorkflowExecutionException workflowStructureError(String executionId, String message) {
        return new WorkflowExecutionException(executionId, null, message,
                ErrorType.WORKFLOW_STRUCTURE_ERROR, false);
    }

    /**
     * 创建超时异常
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param message 消息内容
     * @return 处理结果
     */
    public static WorkflowExecutionException timeoutError(String executionId, String nodeId,
                                                          String message) {
        return new WorkflowExecutionException(executionId, nodeId, message,
                ErrorType.TIMEOUT_ERROR, true);
    }

    /**
     * 创建资源不可用异常
     * @param message 消息内容
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @return 处理结果
     */
    public static WorkflowExecutionException resourceUnavailable(String executionId, String nodeId,
                                                                 String message) {
        return new WorkflowExecutionException(executionId, nodeId, message,
                ErrorType.RESOURCE_UNAVAILABLE, true);
    }

    /**
     * 错误类型枚举
     */
    public enum ErrorType implements AiEnum {
        /**
         * 节点执行错误
         */
        NODE_EXECUTION_ERROR,

        /**
         * 节点配置错误
         */
        NODE_CONFIG_ERROR,

        /**
         * 工作流结构错误
         */
        WORKFLOW_STRUCTURE_ERROR,

        /**
         * 超时错误
         */
        TIMEOUT_ERROR,

        /**
         * 资源不可用
         */
        RESOURCE_UNAVAILABLE,

        /**
         * 用户取消
         */
        USER_CANCELLED,

        /**
         * 未知错误
         */
        UNKNOWN
    }
}
