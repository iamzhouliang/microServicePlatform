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

package com.microservice.platform.ai.core.workflow.runtime;

import java.util.List;
import java.util.Map;

/**
 * 智能体工作流执行结果。
 *
 * @param errorMessage 错误信息
 * @param executedNodeIds executedNodeIds 参数
 * @param outputs 输出数据
 * @param scope scope 参数
 * @param snapshot snapshot 参数
 * @param success success 参数
 * @author xJh
 * @since 2026/05/24
 */
public record WorkflowExecutionResult(
        boolean success,
        WorkflowExecutionScope scope,
        Map<String, Object> outputs,
        List<String> executedNodeIds,
        String errorMessage,
        WorkflowExecutionSnapshot snapshot) {

    public static WorkflowExecutionResult success(WorkflowExecutionScope scope, Map<String, Object> outputs,
                                                 List<String> executedNodeIds,
                                                 WorkflowExecutionSnapshot snapshot) {
        return new WorkflowExecutionResult(true, scope, outputs, List.copyOf(executedNodeIds), null, snapshot);
    }

    public static WorkflowExecutionResult failure(WorkflowExecutionScope scope, String errorMessage,
                                                 List<String> executedNodeIds,
                                                 WorkflowExecutionSnapshot snapshot) {
        return new WorkflowExecutionResult(false, scope, Map.of(), List.copyOf(executedNodeIds), errorMessage, snapshot);
    }
}
