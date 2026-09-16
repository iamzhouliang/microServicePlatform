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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作流验证结果
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowValidationResult {

    /**
     * 是否验证通过
     */
    private boolean valid;

    /**
     * 错误信息列表
     */
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    /**
     * 警告信息列表
     */
    @Builder.Default
    private List<String> warnings = new ArrayList<>();

    /**
     * 结构化诊断问题。
     */
    @Builder.Default
    private List<WorkflowDiagnosticIssue> issues = new ArrayList<>();

    /**
     * 创建成功的验证结果
     * @return 处理结果
     */
    public static WorkflowValidationResult success() {
        return WorkflowValidationResult.builder()
                .valid(true)
                .build();
    }

    /**
     * 创建失败的验证结果
     * @param error error 参数
     * @return 处理结果
     */
    public static WorkflowValidationResult failure(String error) {
        WorkflowValidationResult result = new WorkflowValidationResult();
        result.setValid(false);
        result.getErrors().add(error);
        return result;
    }

    /**
     * 创建失败的验证结果（多个错误）
     * @param errors errors 参数
     * @return 处理结果
     */
    public static WorkflowValidationResult failure(List<String> errors) {
        WorkflowValidationResult result = new WorkflowValidationResult();
        result.setValid(false);
        result.setErrors(new ArrayList<>(errors));
        return result;
    }

    /**
     * 添加错误
     * @param error error 参数
     */
    public void addError(String error) {
        if (this.errors == null) {
            this.errors = new ArrayList<>();
        }
        this.errors.add(error);
        this.valid = false;
    }

    /**
     * 添加阻断问题。
     * @param code 编码
     * @param error error 参数
     * @param nodeId 节点标识
     * @param nodeLabel nodeLabel 参数
     * @param nodeType nodeType 参数
     * @param suggestion suggestion 参数
     */
    public void addError(String code, String error, String nodeId, String nodeLabel, com.microservice.platform.ai.core.enums.NodeType nodeType, String suggestion) {
        addIssue(WorkflowDiagnosticIssue.builder()
                .code(code)
                .severity(WorkflowDiagnosticIssue.Severity.ERROR)
                .nodeId(nodeId)
                .nodeLabel(nodeLabel)
                .nodeType(nodeType)
                .message(error)
                .suggestion(suggestion)
                .build());
    }

    /**
     * 添加警告
     * @param warning warning 参数
     */
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }

    /**
     * 添加警告问题。
     * @param code 编码
     * @param nodeId 节点标识
     * @param nodeLabel nodeLabel 参数
     * @param nodeType nodeType 参数
     * @param suggestion suggestion 参数
     * @param warning warning 参数
     */
    public void addWarning(String code, String warning, String nodeId, String nodeLabel, com.microservice.platform.ai.core.enums.NodeType nodeType, String suggestion) {
        addIssue(WorkflowDiagnosticIssue.builder()
                .code(code)
                .severity(WorkflowDiagnosticIssue.Severity.WARNING)
                .nodeId(nodeId)
                .nodeLabel(nodeLabel)
                .nodeType(nodeType)
                .message(warning)
                .suggestion(suggestion)
                .build());
    }

    /**
     * 添加建议问题。
     * @param code 编码
     * @param message 消息内容
     * @param nodeId 节点标识
     * @param nodeLabel nodeLabel 参数
     * @param nodeType nodeType 参数
     * @param suggestion suggestion 参数
     */
    public void addSuggestion(String code, String message, String nodeId, String nodeLabel, com.microservice.platform.ai.core.enums.NodeType nodeType, String suggestion) {
        addIssue(WorkflowDiagnosticIssue.builder()
                .code(code)
                .severity(WorkflowDiagnosticIssue.Severity.SUGGESTION)
                .nodeId(nodeId)
                .nodeLabel(nodeLabel)
                .nodeType(nodeType)
                .message(message)
                .suggestion(suggestion)
                .build());
    }

    /**
     * 添加结构化诊断问题。
     * @param issue issue 参数
     */
    public void addIssue(WorkflowDiagnosticIssue issue) {
        if (this.issues == null) {
            this.issues = new ArrayList<>();
        }
        this.issues.add(issue);
        if (issue.getSeverity() == WorkflowDiagnosticIssue.Severity.ERROR) {
            addError(issue.getMessage());
        } else if (issue.getSeverity() == WorkflowDiagnosticIssue.Severity.WARNING) {
            addWarning(issue.getMessage());
        }
    }

    /**
     * 合并另一个验证结果
     * @param other other 参数
     */
    public void merge(WorkflowValidationResult other) {
        if (other == null) {
            return;
        }
        if (!other.isValid()) {
            this.valid = false;
        }
        if (other.getErrors() != null) {
            if (this.errors == null) {
                this.errors = new ArrayList<>();
            }
            this.errors.addAll(other.getErrors());
        }
        if (other.getWarnings() != null) {
            if (this.warnings == null) {
                this.warnings = new ArrayList<>();
            }
            this.warnings.addAll(other.getWarnings());
        }
        if (other.getIssues() != null) {
            if (this.issues == null) {
                this.issues = new ArrayList<>();
            }
            this.issues.addAll(other.getIssues());
        }
    }
}
