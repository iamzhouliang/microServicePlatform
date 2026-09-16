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

package com.microservice.platform.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.dto.req.WorkflowExecutionPageReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowExecutionResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowExecution;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Set;

/**
 * 工作流执行服务接口
 * 提供工作流的同步/异步执行、暂停/恢复/取消功能
 *
 * @author xJh
 * @since 2026/01/07
 */
public interface WorkflowExecutionService extends SuperService<WorkflowExecution> {

    /**
     * 同步执行工作流
     *
     * @param workflowId 工作流ID
     * @param inputs     输入参数
     * @return 执行结果
     */
    WorkflowExecutionResp execute(Long workflowId, Map<String, Object> inputs);

    /**
     * 同步执行工作流（带断点）
     *
     * @param workflowId  工作流ID
     * @param inputs      输入参数
     * @param breakpoints 断点节点ID集合
     * @return 执行结果
     */
    WorkflowExecutionResp execute(Long workflowId, Map<String, Object> inputs, Set<String> breakpoints);

    /**
     * 异步执行工作流
     *
     * @param workflowId 工作流ID
     * @param inputs     输入参数
     * @return 执行ID
     */
    String executeAsync(Long workflowId, Map<String, Object> inputs);

    /**
     * 异步执行工作流（带断点）
     *
     * @param workflowId  工作流ID
     * @param inputs      输入参数
     * @param breakpoints 断点节点ID集合
     * @return 执行ID
     */
    String executeAsync(Long workflowId, Map<String, Object> inputs, Set<String> breakpoints);

    /**
     * 暂停工作流执行
     *
     * @param executionId 执行ID
     */
    void pause(String executionId);

    /**
     * 恢复工作流执行
     *
     * @param executionId 执行ID
     */
    void resume(String executionId);

    /**
     * 取消工作流执行
     *
     * @param executionId 执行ID
     */
    void cancel(String executionId);

    /**
     * 获取执行详情
     *
     * @param executionId 执行ID
     * @return 执行详情
     */
    WorkflowExecutionResp getExecution(String executionId);

    /**
     * 分页查询执行历史
     *
     * @param req 分页查询请求
     * @return 执行历史分页结果
     */
    IPage<WorkflowExecutionResp> pageExecutions(WorkflowExecutionPageReq req);

    /**
     * 订阅执行事件（SSE）
     *
     * @param executionId 执行ID
     * @return SSE 发射器
     */
    SseEmitter subscribe(String executionId);

    /**
     * 更新调试变量
     *
     * @param executionId  执行ID
     * @param variableName 变量名
     * @param value        变量值
     */
    void updateVariable(String executionId, String variableName, Object value);

    /**
     * 获取执行快照（用于断点续传）
     *
     * @param executionId 执行ID
     * @return 执行快照
     */
    Map<String, Object> getSnapshot(String executionId);

    /**
     * 从快照恢复执行
     *
     * @param executionId 执行ID
     * @param snapshot    执行快照
     * @return 执行结果
     */
    WorkflowExecutionResp resumeFromSnapshot(String executionId, Map<String, Object> snapshot);
}
