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

package com.microservice.platform.ai.core.workflow.definition;

/**
 * 工作流运行时事件类型。
 */
public enum WorkflowRuntimeEventType {

    WORKFLOW_STARTED("workflow.started"),
    WORKFLOW_RESUMED("workflow.resumed"),
    NODE_STARTED("node.started"),
    NODE_DELTA("node.delta"),
    NODE_COMPLETED("node.completed"),
    NODE_FAILED("node.failed"),
    WORKFLOW_PAUSED("workflow.paused"),
    WORKFLOW_COMPLETED("workflow.completed"),
    WORKFLOW_FAILED("workflow.failed"),
    WORKFLOW_CANCELLED("workflow.cancelled");

    private final String code;

    WorkflowRuntimeEventType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }
}
