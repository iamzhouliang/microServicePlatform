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

package com.microservice.platform.ai.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 工作流节点类型枚举
 * 参考 Dify Workflow 设计: https://docs.dify.ai/en/guides/workflow
 *
 * @author xJh
 * @since 2026/01/06
 */
@Getter
@AllArgsConstructor
public enum NodeType implements AiEnum {

    // ==================== 基础节点 ====================

    /**
     * 开始节点 (用户输入)
     * 定义工作流输入字段，支持多种输入类型
     */
    START("START", "用户输入", "定义工作流输入字段"),

    /**
     * 结束节点 (回答)
     * 定义工作流输出内容
     */
    END("END", "结束/回答", "定义工作流输出"),

    /**
     * 变量赋值节点
     * 设置和转换变量
     */
    VARIABLE_ASSIGNER("VARIABLE_ASSIGNER", "变量赋值", "设置和转换变量"),

    // ==================== AI 节点 ====================

    /**
     * 大模型节点
     * 调用 LLM 进行推理，支持 Vision、Memory、结构化输出
     */
    LLM("LLM", "大模型", "调用 LLM 进行推理"),

    /**
     * 知识检索节点
     * 从知识库检索相关内容，支持元数据过滤
     */
    KNOWLEDGE_RETRIEVAL("KNOWLEDGE_RETRIEVAL", "知识检索", "从知识库检索相关内容"),

    /**
     * 问题分类器节点 (Dify 特色)
     * 使用 LLM 对问题进行分类，路由到不同分支
     */
    QUESTION_CLASSIFIER("QUESTION_CLASSIFIER", "问题分类器", "使用 LLM 对问题进行分类"),

    /**
     * 参数提取器节点 (Dify 特色)
     * 从文本中提取结构化参数
     */
    PARAMETER_EXTRACTOR("PARAMETER_EXTRACTOR", "参数提取器", "从文本中提取结构化参数"),

    /**
     * 智能体节点
     * 调用已配置的智能体
     */
    AGENT("AGENT", "智能体", "调用已配置的智能体"),

    // ==================== 逻辑节点 ====================

    /**
     * 条件分支节点 (IF/ELSE)
     * 根据条件选择执行路径，支持 IF/ELIF/ELSE 多分支
     */
    IF_ELSE("IF_ELSE", "条件分支", "根据条件选择执行路径"),

    /**
     * 迭代节点 (Dify 特色)
     * 对数组元素进行批量处理，支持顺序/并行模式
     */
    ITERATION("ITERATION", "迭代", "对数组元素进行批量处理"),

    /**
     * 变量聚合器节点 (Dify 特色)
     * 合并多分支输出变量
     */
    VARIABLE_AGGREGATOR("VARIABLE_AGGREGATOR", "变量聚合器", "合并多分支输出"),

    /**
     * 循环节点
     * 基于条件的循环执行
     */
    LOOP("LOOP", "循环", "基于条件的循环执行"),

    /**
     * 并行节点
     * 并行执行多个分支
     */
    PARALLEL("PARALLEL", "并行", "并行执行多个分支"),

    // ==================== 数据处理节点 ====================

    /**
     * 代码节点
     * 执行 Python/JavaScript 代码
     */
    CODE("CODE", "代码", "执行 Python/JavaScript 代码"),

    /**
     * 模板转换节点 (Dify 特色)
     * 使用 Jinja2 模板转换数据
     */
    TEMPLATE("TEMPLATE", "模板转换", "使用 Jinja2 模板转换数据"),

    /**
     * 文档提取器节点 (Dify 特色)
     * 从文档中提取文本 (PDF、Word、Excel)
     */
    DOC_EXTRACTOR("DOC_EXTRACTOR", "文档提取器", "从文档中提取文本"),

    /**
     * 列表操作节点 (Dify 特色)
     * 对数组进行过滤、排序、切片等操作
     */
    LIST_OPERATOR("LIST_OPERATOR", "列表操作", "对数组进行过滤、排序等操作"),

    // ==================== 集成节点 ====================

    /**
     * HTTP请求节点
     * 发送 HTTP 请求，支持多种认证方式
     */
    HTTP_REQUEST("HTTP_REQUEST", "HTTP请求", "发送 HTTP 请求"),

    /**
     * 工具节点
     * 调用 MCP 工具
     */
    TOOL("TOOL", "工具", "调用 MCP 工具");

    @EnumValue
    private final String code;
    private final String description;
    private final String detail;

    NodeType(String code, String description) {
        this.code = code;
        this.description = description;
        this.detail = description;
    }

    @JsonCreator
    public static NodeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        return AiEnumUtils.requireCode(NodeType.class, code);
    }

    public static NodeType fromValue(String value) {
        return fromCode(value);
    }

    @JsonValue
    @Override
    public String getCode() {
        return code;
    }

    public String toValue() {
        return code;
    }
}
