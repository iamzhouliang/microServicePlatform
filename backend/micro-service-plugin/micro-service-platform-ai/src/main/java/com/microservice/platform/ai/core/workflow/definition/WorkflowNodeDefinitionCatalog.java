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

import com.microservice.platform.ai.core.enums.NodeType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 工作流节点定义中心。
 */
public final class WorkflowNodeDefinitionCatalog {

    public static final String INPUT = "input";
    public static final String OUTPUT = "output";
    public static final String BRANCH_PREFIX = "branch:";

    private static final WorkflowNodePortDefinition INPUT_PORT =
            new WorkflowNodePortDefinition(INPUT, "输入", "input", true);
    private static final WorkflowNodePortDefinition OUTPUT_PORT =
            new WorkflowNodePortDefinition(OUTPUT, "输出", "output", true);

    private static final Map<NodeType, WorkflowNodeDefinition> DEFINITIONS = new EnumMap<>(NodeType.class);

    static {
        register(NodeType.START, "开始", "定义工作流输入", "basic", "PlayCircleOutlined", "#2f9e44",
                true, false, List.of(), List.of(OUTPUT_PORT),
                schema("StartNodeForm", List.of(
                        field("fields", "输入字段", "FieldList", "array", false, List.of(), "添加输入字段", "定义工作流启动时需要填写的变量")), List.of()),
                Map.of("fields", List.of()));
        register(NodeType.END, "结束", "返回工作流最终输出", "basic", "StopOutlined", "#e03131",
                false, true, List.of(INPUT_PORT), List.of(),
                schema("EndNodeForm", List.of(
                        field("outputs", "输出字段", "OutputList", "array", false, List.of(), "添加输出字段", "定义工作流最终返回内容"),
                        field("outputMode", "输出模式", "Select", "string", false, "TEMPLATE", "选择输出模式", "控制结束节点如何生成响应")), List.of()),
                Map.of("outputs", List.of(), "outputMode", "TEMPLATE"));
        register(NodeType.VARIABLE_ASSIGNER, "变量赋值", "写入或转换运行变量", "data", "DatabaseOutlined", "#7048e8",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("VariableNodeForm", List.of(
                        field("assignments", "赋值规则", "AssignmentList", "array", true, List.of(), "添加赋值规则", "把输入或表达式写入运行变量")), List.of()),
                Map.of("assignments", List.of()));
        register(NodeType.LLM, "LLM", "调用大模型完成推理任务", "ai", "RobotOutlined", "#1971c2",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("LLMNodeForm", List.of(
                        field("modelId", "模型", "ModelSelect", "string", true, null, "请选择模型", "选择用于推理的大模型"),
                        field("systemPrompt", "系统提示词", "Textarea", "string", false, "", "输入系统提示词", "定义模型角色和约束"),
                        field("promptTemplate", "用户提示词", "Textarea", "string", true, "", "输入用户提示词模板", "支持 {{inputs.query}} 变量引用"),
                        field("temperature", "温度", "NumberInput", "number", false, 0.7, "0.7", "控制输出随机性"),
                        field("maxTokens", "最大 Token", "NumberInput", "number", false, 2048, "2048", "限制模型输出长度"),
                        field("outputVariable", "输出变量", "Input", "string", false, "result", "result", "写入运行作用域的变量名")), List.of("result")),
                Map.of("temperature", 0.7, "streaming", true, "outputVariable", "result"));
        register(NodeType.KNOWLEDGE_RETRIEVAL, "知识检索", "从知识库召回上下文", "ai", "BookOutlined", "#0c8599",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("KnowledgeNodeForm", List.of(
                        field("knowledgeBaseIds", "知识库", "KnowledgeBaseSelect", "array", true, List.of(), "请选择知识库", "选择检索来源"),
                        field("queryVariable", "查询变量", "VariableInput", "string", true, "", "{{inputs.query}}", "作为检索 query 的变量"),
                        field("outputVariable", "输出变量", "Input", "string", false, "context", "context", "写入召回上下文")), List.of("context")),
                Map.of("knowledgeBaseIds", List.of(), "outputVariable", "context"));
        register(NodeType.QUESTION_CLASSIFIER, "问题分类器", "根据问题语义选择后续路径", "ai", "BranchesOutlined", "#ae3ec9",
                false, false, List.of(INPUT_PORT), List.of(branch("default", "默认")),
                schema("QuestionClassifierNodeForm", List.of(
                        field("categories", "分类", "CategoryList", "array", true, List.of(), "添加分类", "每个分类对应一个输出分支")), List.of("selectedBranch")),
                Map.of("categories", List.of()));
        register(NodeType.PARAMETER_EXTRACTOR, "参数提取器", "从文本中提取结构化参数", "ai", "FormOutlined", "#1864ab",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("ParameterExtractorNodeForm", List.of(
                        field("sourceVariable", "来源变量", "VariableInput", "string", true, "", "{{inputs.query}}", "需要提取参数的文本来源"),
                        field("parameters", "参数列表", "ParameterList", "array", true, List.of(), "添加参数", "定义要提取的字段和类型")), List.of("parameters")),
                Map.of("parameters", List.of()));
        register(NodeType.AGENT, "智能体", "调用已配置智能体完成任务", "ai", "UserOutlined", "#364fc7",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("AgentNodeForm", List.of(
                        field("agentId", "智能体", "AgentSelect", "string", true, null, "请选择智能体", "选择要调用的已配置智能体"),
                        field("input", "输入", "VariableInput", "string", false, "", "{{inputs.query}}", "传递给智能体的输入")), List.of("result")),
                Map.of());
        register(NodeType.IF_ELSE, "条件分支", "按条件选择执行路径", "control", "ForkOutlined", "#f08c00",
                false, false, List.of(INPUT_PORT), List.of(branch("if", "如果"), branch("else", "否则")),
                schema("IfElseNodeForm", List.of(
                        field("branches", "分支条件", "BranchList", "array", true, List.of(), "添加条件", "按条件选择后续路径")), List.of("selectedBranch")),
                Map.of("branches", List.of()));
        register(NodeType.ITERATION, "迭代", "遍历列表并执行子流程", "control", "RetweetOutlined", "#5c7cfa",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("IterationNodeForm", List.of(
                        field("arrayVariable", "数组变量", "VariableInput", "string", true, "", "{{inputs.items}}", "需要遍历的数组变量"),
                        field("processingMode", "处理模式", "Select", "string", false, "SEQUENTIAL", "SEQUENTIAL", "控制迭代顺序或并行执行"),
                        field("outputVariable", "输出变量", "Input", "string", false, "items", "items", "写入迭代结果")), List.of("items")),
                Map.of("arrayVariable", "", "processingMode", "SEQUENTIAL", "outputVariable", "results"));
        register(NodeType.VARIABLE_AGGREGATOR, "变量聚合器", "合并多路输出为一个变量", "data", "MergeOutlined", "#6741d9",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("VariableAggregatorNodeForm", List.of(
                        field("groups", "聚合组", "AggregatorGroupList", "array", true, List.of(), "添加聚合组", "合并多路变量")), List.of("result")),
                Map.of("groups", List.of()));
        register(NodeType.LOOP, "循环", "按条件重复执行流程", "control", "SyncOutlined", "#5f3dc4",
                false, false, List.of(INPUT_PORT), List.of(branch("loop", "循环"), branch("exit", "退出")),
                schema("LoopNodeForm", List.of(
                        field("maxIterations", "最大迭代次数", "NumberInput", "number", true, 3, "3", "防止无限循环")), List.of("continueLoop")),
                Map.of("maxIterations", 3));
        register(NodeType.PARALLEL, "并行", "并行执行多个分支", "control", "ApartmentOutlined", "#087f5b",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("ParallelNodeForm", List.of(
                        field("waitStrategy", "等待策略", "Select", "string", false, "ALL", "ALL", "控制并行分支等待方式")), List.of("result")),
                Map.of("waitStrategy", "ALL"));
        register(NodeType.CODE, "代码", "运行自定义代码处理数据", "data", "CodeOutlined", "#495057",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("CodeNodeForm", List.of(
                        field("language", "语言", "Select", "string", true, "JAVASCRIPT", "JAVASCRIPT", "选择代码运行语言"),
                        field("code", "代码", "CodeEditor", "string", true, "", "输入代码", "处理输入并返回输出"),
                        field("outputVariable", "输出变量", "Input", "string", false, "result", "result", "写入代码执行结果")), List.of("result")),
                Map.of("language", "JAVASCRIPT", "outputVariable", "result"));
        register(NodeType.TEMPLATE, "模板转换", "用模板拼装文本或结构化内容", "data", "FileTextOutlined", "#c92a2a",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("TemplateNodeForm", List.of(
                        field("template", "模板", "Textarea", "string", true, "", "输入模板", "支持变量引用"),
                        field("outputVariable", "输出变量", "Input", "string", false, "result", "result", "写入模板结果")), List.of("result")),
                Map.of("template", "", "outputVariable", "result"));
        register(NodeType.DOC_EXTRACTOR, "文档提取器", "从文件中提取文本内容", "data", "FileSearchOutlined", "#0b7285",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("DocExtractorNodeForm", List.of(
                        field("fileVariable", "文件变量", "VariableInput", "string", true, "", "{{inputs.file}}", "要提取的文件来源"),
                        field("outputVariable", "输出变量", "Input", "string", false, "text", "text", "写入提取文本")), List.of("text")),
                Map.of("outputVariable", "text"));
        register(NodeType.LIST_OPERATOR, "列表操作", "过滤、映射或聚合列表数据", "data", "UnorderedListOutlined", "#2b8a3e",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("ListOperatorNodeForm", List.of(
                        field("operationType", "操作", "Select", "string", true, "FILTER", "FILTER", "选择列表处理方式"),
                        field("inputVariable", "列表变量", "VariableInput", "string", true, "", "{{inputs.items}}", "要处理的列表变量"),
                        field("outputVariable", "输出变量", "Input", "string", false, "result", "result", "写入列表处理结果")), List.of("result")),
                Map.of("operationType", "FILTER", "outputVariable", "list_result"));
        register(NodeType.HTTP_REQUEST, "HTTP 请求", "调用外部 HTTP 服务", "external", "ApiOutlined", "#1c7ed6",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("HttpNodeForm", List.of(
                        field("method", "请求方法", "Select", "string", true, "GET", "GET", "选择 HTTP 方法"),
                        field("url", "URL", "Input", "string", true, "", "https://api.example.com", "仅支持 http/https"),
                        field("outputVariable", "输出变量", "Input", "string", false, "response", "response", "写入 HTTP 响应")), List.of("response")),
                Map.of("method", "GET", "url", "", "outputVariable", "response"));
        register(NodeType.TOOL, "工具", "调用 MCP 或平台工具", "external", "ToolOutlined", "#e67700",
                false, false, List.of(INPUT_PORT), List.of(OUTPUT_PORT),
                schema("ToolNodeForm", List.of(
                        field("mcpServerId", "MCP 服务器", "McpServerSelect", "string", true, null, "请选择 MCP 服务器", "选择工具来源"),
                        field("toolName", "工具", "ToolSelect", "string", true, null, "请选择工具", "选择要调用的工具"),
                        field("outputVariable", "输出变量", "Input", "string", false, "result", "result", "写入工具调用结果")), List.of("result")),
                Map.of("outputVariable", "result"));
    }

    private WorkflowNodeDefinitionCatalog() {
    }

    public static List<WorkflowNodeDefinition> all() {
        return new ArrayList<>(DEFINITIONS.values());
    }

    public static WorkflowNodeDefinition require(NodeType type) {
        WorkflowNodeDefinition definition = DEFINITIONS.get(type);
        if (definition == null) {
            throw new IllegalArgumentException("未知工作流节点类型: " + type);
        }
        return definition;
    }

    public static String branchHandle(String branchId) {
        return BRANCH_PREFIX + branchId;
    }

    private static WorkflowNodePortDefinition branch(String id, String name) {
        return new WorkflowNodePortDefinition(branchHandle(id), name, "output", true);
    }

    private static void register(
                                 NodeType type,
                                 String displayName,
                                 String description,
                                 String category,
                                 String icon,
                                 String color,
                                 boolean start,
                                 boolean terminal,
                                 List<WorkflowNodePortDefinition> inputs,
                                 List<WorkflowNodePortDefinition> outputs,
                                 WorkflowNodeConfigSchema configSchema,
                                 Map<String, Object> defaultConfig) {
        DEFINITIONS.put(type, new WorkflowNodeDefinition(
                type,
                displayName,
                description,
                category,
                icon,
                color,
                start,
                terminal,
                inputs,
                outputs,
                configSchema,
                defaultConfig));
    }

    private static WorkflowNodeConfigSchema schema(String formComponent,
                                                   List<WorkflowNodeConfigField> fields,
                                                   List<String> outputVariables) {
        List<String> requiredFields = fields.stream()
                .filter(WorkflowNodeConfigField::required)
                .map(WorkflowNodeConfigField::name)
                .toList();
        return new WorkflowNodeConfigSchema(formComponent, requiredFields, outputVariables, fields);
    }

    private static WorkflowNodeConfigField field(String name, String label, String component, String valueType,
                                                 boolean required, Object defaultValue, String placeholder, String help) {
        return new WorkflowNodeConfigField(name, label, component, valueType, required, defaultValue,
                placeholder, help, List.of(), List.of());
    }
}
