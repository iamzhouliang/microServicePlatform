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

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowEdge;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("工作流发布级诊断器")
class WorkflowValidatorTest {

    private final WorkflowValidator validator = new WorkflowValidator();

    @Test
    @DisplayName("识别 Agent、Tool 和 HTTP 节点的发布阻断配置问题")
    void validateDetectsAgentToolAndHttpBlockingIssues() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "输入边界", Map.of("fields", List.of(Map.of("name", "question")))),
                        node("llm", NodeType.LLM, "模型智能体", Map.of("promptTemplate", "回答 {{inputs.question}}")),
                        node("tool", NodeType.TOOL, "工具能力", Map.of("mcpServerId", 10)),
                        node("http", NodeType.HTTP_REQUEST, "接口调用", Map.of("method", "POST", "url", "ftp://example.com")),
                        node("end", NodeType.END, "输出边界", Map.of("outputs", List.of(Map.of("name", "answer", "value", "{{nodes.llm.answer}}"))))),
                List.of(edge("start", "llm"), edge("llm", "tool"), edge("tool", "http"), edge("http", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(WorkflowDiagnosticIssue::getCode)
                .contains("AGENT_MODEL_REQUIRED", "TOOL_NAME_REQUIRED", "HTTP_URL_INVALID");
        assertThat(result.getErrors()).anyMatch(error -> error.contains("模型智能体"));
    }

    @Test
    @DisplayName("识别未知变量引用和运行成本风险")
    void validateDetectsUnknownVariablesAndRuntimeRisk() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "输入边界", Map.of("fields", List.of(Map.of("name", "question")))),
                        node("parallel", NodeType.PARALLEL, "并行路由"),
                        node("left", NodeType.TEMPLATE, "左分支", Map.of("template", "{{inputs.missing}}")),
                        node("right", NodeType.TEMPLATE, "右分支", Map.of("template", "{{nodes.ghost.value}}")),
                        node("loop", NodeType.LOOP, "循环路由", Map.of("maxIterations", 101)),
                        node("end", NodeType.END, "输出边界", Map.of("outputs", List.of(Map.of("name", "answer", "value", "{{nodes.left.result}}"))))),
                List.of(edge("start", "parallel"), edge("parallel", "left"), edge("parallel", "right"), edge("left", "loop"), edge("right", "loop"), edge("loop", "branch:exit", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(WorkflowDiagnosticIssue::getCode)
                .contains("VARIABLE_INPUT_UNKNOWN", "VARIABLE_NODE_UNKNOWN", "LOOP_MAX_ITERATIONS_HIGH");
        assertThat(result.getWarnings()).anyMatch(warning -> warning.contains("101"));
    }

    @Test
    @DisplayName("合法的 工作流图只产生低风险建议")
    void validateAcceptsMarketGradeWorkflowGraph() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "输入边界", Map.of("fields", List.of(Map.of("name", "question")))),
                        node("retrieval", NodeType.KNOWLEDGE_RETRIEVAL, "检索智能体", Map.of("knowledgeBaseIds", List.of(1), "queryVariable", "{{inputs.question}}")),
                        node("llm", NodeType.LLM, "模型智能体", Map.of("modelId", 1, "promptTemplate", "基于 {{nodes.retrieval.documents}} 回答 {{inputs.question}}", "outputVariable", "answer")),
                        node("end", NodeType.END, "输出边界", Map.of("outputs", List.of(Map.of("name", "answer", "value", "{{nodes.llm.answer}}"))))),
                List.of(edge("start", "retrieval"), edge("retrieval", "llm"), edge("llm", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getIssues()).noneMatch(issue -> issue.getSeverity() == WorkflowDiagnosticIssue.Severity.ERROR);
    }

    @Test
    @DisplayName("市场级全节点类型图可以通过发布诊断")
    void validateAcceptsAllSupportedNodeTypesInOneFlow() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "输入边界", Map.of("fields", List.of(
                                Map.of("name", "question"),
                                Map.of("name", "files"),
                                Map.of("name", "items")))),
                        node("assign", NodeType.VARIABLE_ASSIGNER, "作用域写入", Map.of("assignments", List.of(
                                Map.of("variable", "normalizedQuestion", "value", "{{inputs.question}}")))),
                        node("agent", NodeType.AGENT, "子智能体", Map.of("agentId", 1001, "input", "{{nodes.assign.normalizedQuestion}}")),
                        node("retrieval", NodeType.KNOWLEDGE_RETRIEVAL, "知识检索", Map.of(
                                "knowledgeBaseIds", List.of(1, 2),
                                "queryVariable", "{{inputs.question}}")),
                        node("classifier", NodeType.QUESTION_CLASSIFIER, "问题分类", Map.of("categories", List.of(
                                Map.of("id", "tech", "name", "技术"),
                                Map.of("id", "biz", "name", "业务")))),
                        node("llm", NodeType.LLM, "模型推理", Map.of(
                                "modelId", 1,
                                "promptTemplate", "结合 {{nodes.retrieval.documents}} 回答 {{inputs.question}}",
                                "outputVariable", "answer")),
                        node("parameter", NodeType.PARAMETER_EXTRACTOR, "参数提取", Map.of(
                                "sourceVariable", "{{nodes.llm.answer}}",
                                "parameters", List.of(Map.of("name", "topic", "type", "STRING")))),
                        node("ifElse", NodeType.IF_ELSE, "质量分支", Map.of("branches", List.of(
                                Map.of("id", "ok", "type", "IF"),
                                Map.of("id", "fallback", "type", "ELSE")))),
                        node("code", NodeType.CODE, "代码处理", Map.of("language", "JAVASCRIPT", "code", "return inputs;")),
                        node("template", NodeType.TEMPLATE, "模板转换", Map.of("template", "答案：{{nodes.llm.answer}}")),
                        node("doc", NodeType.DOC_EXTRACTOR, "文档提取", Map.of("fileVariable", "{{inputs.files}}")),
                        node("list", NodeType.LIST_OPERATOR, "列表处理", Map.of("inputVariable", "{{inputs.items}}", "operationType", "FILTER")),
                        node("iteration", NodeType.ITERATION, "迭代处理", Map.of("arrayVariable", "{{inputs.items}}")),
                        node("parallel", NodeType.PARALLEL, "并行处理"),
                        node("http", NodeType.HTTP_REQUEST, "外部接口", Map.of("method", "POST", "url", "https://api.example.com/workflow")),
                        node("tool", NodeType.TOOL, "MCP 工具", Map.of("mcpServerId", 2, "toolName", "search", "outputVariable", "toolResult")),
                        node("aggregator", NodeType.VARIABLE_AGGREGATOR, "变量聚合", Map.of("groups", List.of(
                                Map.of("name", "httpResult", "variable", "{{nodes.http.body}}"),
                                Map.of("name", "toolResult", "variable", "{{nodes.tool.toolResult}}")))),
                        node("loop", NodeType.LOOP, "循环确认", Map.of("maxIterations", 3)),
                        node("end", NodeType.END, "输出边界", Map.of("outputs", List.of(
                                Map.of("name", "answer", "value", "{{nodes.aggregator.result}}"))))),
                List.of(
                        edge("start", "assign"),
                        edge("assign", "agent"),
                        edge("agent", "retrieval"),
                        edge("retrieval", "classifier"),
                        edge("classifier", "branch:tech", "llm"),
                        edge("classifier", "branch:biz", "parameter"),
                        edge("llm", "ifElse"),
                        edge("parameter", "ifElse"),
                        edge("ifElse", "branch:ok", "parallel"),
                        edge("ifElse", "branch:fallback", "template"),
                        edge("template", "parallel"),
                        edge("parallel", "code"),
                        edge("parallel", "doc"),
                        edge("parallel", "list"),
                        edge("code", "iteration"),
                        edge("doc", "iteration"),
                        edge("list", "iteration"),
                        edge("iteration", "http"),
                        edge("http", "tool"),
                        edge("tool", "aggregator"),
                        edge("aggregator", "loop"),
                        edge("loop", "branch:exit", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getIssues()).noneMatch(issue -> issue.getSeverity() == WorkflowDiagnosticIssue.Severity.ERROR);
    }

    @Test
    @DisplayName("拒绝旧版 output-* 分支端口")
    void validateRejectsLegacyBranchHandles() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始", Map.of("fields", List.of())),
                        node("ifElse", NodeType.IF_ELSE, "条件分支", Map.of("branches", List.of(
                                Map.of("id", "ok", "type", "IF"),
                                Map.of("id", "fallback", "type", "ELSE")))),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of()))),
                List.of(edge("start", "ifElse"), edge("ifElse", "output-ok", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(WorkflowDiagnosticIssue::getCode)
                .contains("BRANCH_HANDLE_UNKNOWN");
    }

    @Test
    @DisplayName("旧版 prompt 字段不能替代 promptTemplate")
    void validateRejectsLegacyLlmPromptField() {
        WorkflowGraph graph = graph(
                List.of(
                        node("start", NodeType.START, "开始", Map.of("fields", List.of())),
                        node("llm", NodeType.LLM, "模型智能体", Map.of("modelId", 1, "prompt", "回答 {{inputs.question}}")),
                        node("end", NodeType.END, "结束", Map.of("outputs", List.of()))),
                List.of(edge("start", "llm"), edge("llm", "end")));

        WorkflowValidationResult result = validator.validate(graph);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getIssues()).extracting(WorkflowDiagnosticIssue::getCode)
                .contains("NODE_CONFIG_REQUIRED", "AGENT_PROMPT_EMPTY");
    }

    private static WorkflowGraph graph(List<WorkflowNode> nodes, List<WorkflowEdge> edges) {
        return WorkflowGraph.builder().nodes(nodes).edges(edges).build();
    }

    private static WorkflowNode node(String id, NodeType type, String label) {
        return node(id, type, label, Map.of());
    }

    private static WorkflowNode node(String id, NodeType type, String label, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(label)
                .position(WorkflowNode.Position.builder().x(0.0).y(0.0).build())
                .data(new HashMap<>(data))
                .build();
    }

    private static WorkflowEdge edge(String source, String target) {
        return edge(source, null, target);
    }

    private static WorkflowEdge edge(String source, String sourceHandle, String target) {
        return WorkflowEdge.builder()
                .id(source + "-" + target)
                .source(source)
                .sourceHandle(sourceHandle)
                .target(target)
                .targetHandle("input")
                .build();
    }
}
