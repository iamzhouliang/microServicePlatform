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

package com.microservice.platform.ai.core.workflow;

import com.microservice.platform.ai.core.enums.AiEnum;
import com.microservice.platform.ai.core.enums.NodeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工作流诊断问题。
 * 用于给发布前诊断、前端面板和开放 API 提供统一结构化问题。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowDiagnosticIssue {

    /**
     * 严重级别。
     */
    public enum Severity implements AiEnum {
        ERROR,
        WARNING,
        SUGGESTION
    }

    /**
     * 问题代码。
     */
    private String code;

    /**
     * 严重级别。
     */
    private Severity severity;

    /**
     * 节点 ID。
     */
    private String nodeId;

    /**
     * 节点名称。
     */
    private String nodeLabel;

    /**
     * 节点类型。
     */
    private NodeType nodeType;

    /**
     * 人类可读消息。
     */
    private String message;

    /**
     * 修复建议。
     */
    private String suggestion;

    public boolean isBlocking() {
        return severity == Severity.ERROR;
    }
}
