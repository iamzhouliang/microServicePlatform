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
import java.util.Set;

/**
 * 可持久化的调度器状态，用于恢复智能体工作流且不重放已完成节点。
 *
 * @param activatedIncomingSources activatedIncomingSources 参数
 * @param executedNodeIds executedNodeIds 参数
 * @param pendingNodeIds pendingNodeIds 参数
 * @param scope scope 参数
 * @author xJh
 * @since 2026/05/24
 */
public record WorkflowExecutionSnapshot(
        Map<String, Object> scope,
        List<String> pendingNodeIds,
        Set<String> executedNodeIds,
        Map<String, Set<String>> activatedIncomingSources) {

    public static WorkflowExecutionSnapshot empty() {
        return new WorkflowExecutionSnapshot(Map.of(), List.of(), Set.of(), Map.of());
    }
}
