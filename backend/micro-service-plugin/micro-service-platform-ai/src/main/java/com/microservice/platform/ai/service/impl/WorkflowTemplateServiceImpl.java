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

package com.microservice.platform.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.enums.TemplateCategory;
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplatePageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplateSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowTemplateResp;
import com.microservice.platform.ai.domain.entity.workflow.*;
import com.microservice.platform.ai.repository.workflow.WorkflowDefinitionMapper;
import com.microservice.platform.ai.repository.workflow.WorkflowTemplateMapper;
import com.microservice.platform.ai.service.WorkflowTemplateService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 工作流模板服务实现类
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowTemplateServiceImpl extends SuperServiceImpl<WorkflowTemplateMapper, WorkflowTemplate>
        implements
            WorkflowTemplateService {

    private final AuthenticationContext context;
    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final ObjectMapper objectMapper;

    @Override
    public IPage<WorkflowTemplateResp> pageList(WorkflowTemplatePageReq req) {
        return baseMapper.selectPage(req.buildPage(), Wraps.<WorkflowTemplate>lbQ()
                .like(StringUtils.hasText(req.getName()), WorkflowTemplate::getName, req.getName())
                .eq(StringUtils.hasText(req.getCategory()), WorkflowTemplate::getCategory, req.getCategory())
                .eq(Boolean.TRUE.equals(req.getBuiltInOnly()), WorkflowTemplate::getBuiltIn, true)
                .and(w -> w.eq(WorkflowTemplate::getBuiltIn, true)
                        .or()
                        .eq(WorkflowTemplate::getUserId, context.userId()))
                .orderByDesc(WorkflowTemplate::getBuiltIn)
                .orderByDesc(WorkflowTemplate::getCreateTime))
                .convert(this::convertToResp);
    }

    @Override
    public WorkflowTemplateResp detail(Long id) {
        WorkflowTemplate template = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("模板不存在"));
        checkReadable(template);
        return convertToResp(template);
    }

    @Override
    public List<WorkflowTemplateResp> listBuiltIn() {
        List<WorkflowTemplate> templates = baseMapper.selectBuiltInTemplates();
        return templates.stream()
                .map(this::convertToResp)
                .toList();
    }

    @Override
    public List<WorkflowTemplateResp> listByCategory(String category) {
        Long userId = context.userId();
        // 仅返回内置模板或归属当前用户的模板，避免按分类枚举泄露他人私有模板
        return baseMapper.selectByCategory(category).stream()
                .filter(t -> Boolean.TRUE.equals(t.getBuiltIn()) || Objects.equals(t.getUserId(), userId))
                .map(this::convertToResp)
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkflowTemplateSaveReq req) {
        // 验证分类
        validateCategory(req.getCategory());

        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(req.getName())
                .description(req.getDescription())
                .category(req.getCategory())
                .icon(req.getIcon())
                .graph(req.getGraph())
                .builtIn(false)
                .userId(context.userId())
                .tenantId(context.tenantId())
                .deleted(false)
                .build();

        baseMapper.insert(template);
        log.info("创建模板成功，ID: {}, 名称: {}", template.getId(), template.getName());
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromWorkflow(Long workflowId, String name, String description, String category) {
        // 获取工作流并校验归属，防止用他人工作流图复制成自己的模板（窃取图定义）
        WorkflowDefinition workflow = Optional.ofNullable(workflowDefinitionMapper.selectById(workflowId))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));
        if (!Objects.equals(workflow.getUserId(), context.userId())) {
            throw CheckedException.forbidden("无权限使用此工作流创建模板");
        }

        // 验证分类
        validateCategory(category);

        WorkflowTemplate template = WorkflowTemplate.builder()
                .name(name)
                .description(description)
                .category(category)
                .graph(workflow.getGraph())
                .builtIn(false)
                .userId(context.userId())
                .tenantId(context.tenantId())
                .deleted(false)
                .build();

        baseMapper.insert(template);
        log.info("从工作流创建模板成功，模板ID: {}, 工作流ID: {}", template.getId(), workflowId);
        return template.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, WorkflowTemplateSaveReq req) {
        WorkflowTemplate template = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("模板不存在"));

        // 不能修改内置模板
        if (Boolean.TRUE.equals(template.getBuiltIn())) {
            throw CheckedException.badRequest("不能修改内置模板");
        }

        // 检查权限
        checkOwnership(template);

        // 验证分类
        validateCategory(req.getCategory());

        template.setName(req.getName());
        template.setDescription(req.getDescription());
        template.setCategory(req.getCategory());
        template.setIcon(req.getIcon());
        template.setGraph(req.getGraph());

        baseMapper.updateById(template);
        log.info("更新模板成功，ID: {}, 名称: {}", id, template.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WorkflowTemplate template = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("模板不存在"));

        // 不能删除内置模板
        if (Boolean.TRUE.equals(template.getBuiltIn())) {
            throw CheckedException.badRequest("不能删除内置模板");
        }

        // 检查权限
        checkOwnership(template);

        baseMapper.deleteById(id);
        log.info("删除模板成功，ID: {}", id);
    }

    @Override
    public String exportTemplate(Long id) {
        WorkflowTemplate template = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("模板不存在"));
        checkReadable(template);

        try {
            // 导出时移除敏感信息
            WorkflowTemplateResp exportData = convertToResp(template);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(exportData);
        } catch (JsonProcessingException e) {
            throw CheckedException.badRequest("导出模板失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long importTemplate(String json) {
        try {
            WorkflowTemplateSaveReq req = objectMapper.readValue(json, WorkflowTemplateSaveReq.class);
            return create(req);
        } catch (JsonProcessingException e) {
            throw CheckedException.badRequest("导入模板失败，JSON 格式错误: " + e.getMessage());
        }
    }

    @Override
    @PostConstruct
    @Transactional(rollbackFor = Exception.class)
    public void initBuiltInTemplates() {
        // 检查是否已初始化
        List<WorkflowTemplate> existingTemplates = baseMapper.selectBuiltInTemplates();
        if (!existingTemplates.isEmpty()) {
            log.info("内置模板已存在，跳过初始化");
            return;
        }

        log.info("开始初始化内置模板...");

        // 创建预定义模板
        List<WorkflowTemplate> templates = createPredefinedTemplates();
        for (WorkflowTemplate template : templates) {
            baseMapper.insert(template);
            log.info("创建内置模板: {}", template.getName());
        }

        log.info("内置模板初始化完成，共创建 {} 个模板", templates.size());
    }

    /**
     * 创建预定义模板
     * @return 处理结果
     */
    private List<WorkflowTemplate> createPredefinedTemplates() {
        List<WorkflowTemplate> templates = new ArrayList<>();

        // 1. RAG 问答模板
        templates.add(createRagTemplate());

        // 2. 文档摘要模板
        templates.add(createSummaryTemplate());

        // 3. 数据提取模板
        templates.add(createExtractionTemplate());

        // 4. 多轮对话模板
        templates.add(createConversationTemplate());

        // 5. 内容生成模板
        templates.add(createGenerationTemplate());

        return templates;
    }

    /**
     * 创建 RAG 问答模板
     * @return 处理结果
     */
    private WorkflowTemplate createRagTemplate() {
        List<WorkflowNode> nodes = new ArrayList<>();
        final List<WorkflowEdge> edges = new ArrayList<>();

        // START 节点
        nodes.add(createNode("start-1", NodeType.START, "开始", 100, 100,
                Map.of("fields", List.of(Map.of("name", "question", "label", "问题")))));

        // 知识库检索节点
        nodes.add(createNode("knowledge-1", NodeType.KNOWLEDGE_RETRIEVAL, "知识库检索", 100, 200,
                Map.of("knowledgeBaseIds", List.of(1L),
                        "queryVariable", "{{inputs.question}}",
                        "outputVariable", "context")));

        // LLM 节点
        nodes.add(createNode("llm-1", NodeType.LLM, "LLM 回答", 100, 300,
                Map.of("systemPrompt", "你是一个智能助手，请根据提供的上下文回答用户问题。",
                        "modelId", 1L,
                        "promptTemplate", "上下文：{{nodes.knowledge-1.context}}\n\n问题：{{inputs.question}}",
                        "outputVariable", "answer")));

        // END 节点
        nodes.add(createNode("end-1", NodeType.END, "结束", 100, 400,
                Map.of("outputs", List.of(Map.of("name", "answer", "value", "{{nodes.llm-1.answer}}")))));

        // 连接边
        edges.add(createEdge("edge-1", "start-1", "output", "knowledge-1", "input"));
        edges.add(createEdge("edge-2", "knowledge-1", "output", "llm-1", "input"));
        edges.add(createEdge("edge-3", "llm-1", "output", "end-1", "input"));

        return WorkflowTemplate.builder()
                .name("RAG 问答")
                .description("基于知识库的智能问答模板，先检索相关文档，再由 LLM 生成回答")
                .category(TemplateCategory.RAG.getCode())
                .icon("BookOutlined")
                .graph(WorkflowGraph.builder().nodes(nodes).edges(edges).build())
                .builtIn(true)
                .deleted(false)
                .build();
    }

    /**
     * 创建文档摘要模板
     * @return 处理结果
     */
    private WorkflowTemplate createSummaryTemplate() {
        List<WorkflowNode> nodes = new ArrayList<>();
        final List<WorkflowEdge> edges = new ArrayList<>();

        // START 节点
        nodes.add(createNode("start-1", NodeType.START, "开始", 100, 100,
                Map.of("fields", List.of(Map.of("name", "document", "label", "文档内容")))));

        // LLM 摘要节点
        nodes.add(createNode("llm-1", NodeType.LLM, "生成摘要", 100, 200,
                Map.of("systemPrompt", "你是一个专业的文档摘要助手，请对提供的文档内容生成简洁准确的摘要。",
                        "modelId", 1L,
                        "promptTemplate", "请对以下文档生成摘要：\n\n{{inputs.document}}",
                        "outputVariable", "summary")));

        // END 节点
        nodes.add(createNode("end-1", NodeType.END, "结束", 100, 300,
                Map.of("outputs", List.of(Map.of("name", "summary", "value", "{{nodes.llm-1.summary}}")))));

        // 连接边
        edges.add(createEdge("edge-1", "start-1", "output", "llm-1", "input"));
        edges.add(createEdge("edge-2", "llm-1", "output", "end-1", "input"));

        return WorkflowTemplate.builder()
                .name("文档摘要")
                .description("自动生成文档摘要的模板，输入文档内容，输出简洁的摘要")
                .category(TemplateCategory.SUMMARY.getCode())
                .icon("FileTextOutlined")
                .graph(WorkflowGraph.builder().nodes(nodes).edges(edges).build())
                .builtIn(true)
                .deleted(false)
                .build();
    }

    /**
     * 创建数据提取模板
     * @return 处理结果
     */
    private WorkflowTemplate createExtractionTemplate() {
        List<WorkflowNode> nodes = new ArrayList<>();
        final List<WorkflowEdge> edges = new ArrayList<>();

        // START 节点
        nodes.add(createNode("start-1", NodeType.START, "开始", 100, 100,
                Map.of("fields", List.of(
                        Map.of("name", "text", "label", "待提取文本"),
                        Map.of("name", "fields", "label", "提取字段")))));

        // LLM 提取节点
        nodes.add(createNode("llm-1", NodeType.LLM, "数据提取", 100, 200,
                Map.of("systemPrompt", "你是一个数据提取专家，请从文本中提取结构化信息，以 JSON 格式输出。",
                        "modelId", 1L,
                        "promptTemplate", "请从以下文本中提取关键信息：\n\n{{inputs.text}}\n\n提取字段：{{inputs.fields}}",
                        "outputVariable", "extracted_data")));

        // END 节点
        nodes.add(createNode("end-1", NodeType.END, "结束", 100, 300,
                Map.of("outputs", List.of(Map.of("name", "extracted_data", "value", "{{nodes.llm-1.extracted_data}}")))));

        // 连接边
        edges.add(createEdge("edge-1", "start-1", "output", "llm-1", "input"));
        edges.add(createEdge("edge-2", "llm-1", "output", "end-1", "input"));

        return WorkflowTemplate.builder()
                .name("数据提取")
                .description("从非结构化文本中提取结构化数据的模板")
                .category(TemplateCategory.EXTRACTION.getCode())
                .icon("DatabaseOutlined")
                .graph(WorkflowGraph.builder().nodes(nodes).edges(edges).build())
                .builtIn(true)
                .deleted(false)
                .build();
    }

    /**
     * 创建多轮对话模板
     * @return 处理结果
     */
    private WorkflowTemplate createConversationTemplate() {
        List<WorkflowNode> nodes = new ArrayList<>();
        final List<WorkflowEdge> edges = new ArrayList<>();

        // START 节点
        nodes.add(createNode("start-1", NodeType.START, "开始", 100, 100,
                Map.of("fields", List.of(Map.of("name", "message", "label", "用户消息")))));

        // 智能体节点
        nodes.add(createNode("agent-1", NodeType.AGENT, "对话智能体", 100, 200,
                Map.of("agentId", 1L, "input", "{{inputs.message}}", "outputVariable", "response")));

        // END 节点
        nodes.add(createNode("end-1", NodeType.END, "结束", 100, 300,
                Map.of("outputs", List.of(Map.of("name", "response", "value", "{{nodes.agent-1.response}}")))));

        // 连接边
        edges.add(createEdge("edge-1", "start-1", "output", "agent-1", "input"));
        edges.add(createEdge("edge-2", "agent-1", "output", "end-1", "input"));

        return WorkflowTemplate.builder()
                .name("多轮对话")
                .description("支持上下文记忆的多轮对话模板，可配置智能体进行连续对话")
                .category(TemplateCategory.CONVERSATION.getCode())
                .icon("MessageOutlined")
                .graph(WorkflowGraph.builder().nodes(nodes).edges(edges).build())
                .builtIn(true)
                .deleted(false)
                .build();
    }

    /**
     * 创建内容生成模板
     * @return 处理结果
     */
    private WorkflowTemplate createGenerationTemplate() {
        List<WorkflowNode> nodes = new ArrayList<>();
        final List<WorkflowEdge> edges = new ArrayList<>();

        // START 节点
        nodes.add(createNode("start-1", NodeType.START, "开始", 100, 100,
                Map.of("fields", List.of(
                        Map.of("name", "topic", "label", "主题"),
                        Map.of("name", "style", "label", "风格"),
                        Map.of("name", "length", "label", "长度")))));

        // LLM 生成节点
        nodes.add(createNode("llm-1", NodeType.LLM, "内容生成", 100, 200,
                Map.of("systemPrompt", "你是一个专业的内容创作者，请根据用户的要求生成高质量的内容。",
                        "modelId", 1L,
                        "promptTemplate", "请根据以下要求生成内容：\n\n主题：{{inputs.topic}}\n风格：{{inputs.style}}\n长度：{{inputs.length}}",
                        "outputVariable", "content")));

        // END 节点
        nodes.add(createNode("end-1", NodeType.END, "结束", 100, 300,
                Map.of("outputs", List.of(Map.of("name", "content", "value", "{{nodes.llm-1.content}}")))));

        // 连接边
        edges.add(createEdge("edge-1", "start-1", "output", "llm-1", "input"));
        edges.add(createEdge("edge-2", "llm-1", "output", "end-1", "input"));

        return WorkflowTemplate.builder()
                .name("内容生成")
                .description("根据主题和风格要求自动生成内容的模板")
                .category(TemplateCategory.GENERATION.getCode())
                .icon("EditOutlined")
                .graph(WorkflowGraph.builder().nodes(nodes).edges(edges).build())
                .builtIn(true)
                .deleted(false)
                .build();
    }

    /**
     * 创建节点
     * @param data 业务数据
     * @param id 主键标识
     * @param label label 参数
     * @param type 类型
     * @param x x 参数
     * @param y y 参数
     * @return 处理结果
     */
    private WorkflowNode createNode(String id, NodeType type, String label, double x, double y, Map<String, Object> data) {
        return WorkflowNode.builder()
                .id(id)
                .type(type)
                .label(label)
                .position(WorkflowNode.Position.builder().x(x).y(y).build())
                .data(new HashMap<>(data))
                .build();
    }

    /**
     * 创建边
     * @param id 主键标识
     * @param source source 参数
     * @param sourceHandle sourceHandle 参数
     * @param target target 参数
     * @param targetHandle targetHandle 参数
     * @return 处理结果
     */
    private WorkflowEdge createEdge(String id, String source, String sourceHandle, String target, String targetHandle) {
        return WorkflowEdge.builder()
                .id(id)
                .source(source)
                .sourceHandle(sourceHandle)
                .target(target)
                .targetHandle(targetHandle)
                .build();
    }

    /**
     * 验证分类
     * @param category category 参数
     */
    private void validateCategory(String category) {
        boolean valid = Arrays.stream(TemplateCategory.values())
                .anyMatch(c -> c.getCode().equals(category));
        if (!valid) {
            throw CheckedException.badRequest("无效的模板分类: " + category);
        }
    }

    /**
     * 检查所有权
     * @param template 模板内容
     */
    private void checkOwnership(WorkflowTemplate template) {
        Long currentUserId = context.userId();
        if (!template.getUserId().equals(currentUserId)) {
            throw CheckedException.forbidden("无权限操作此模板");
        }
    }

    /**
     * 校验模板可读：内置模板对所有人可读，非内置模板仅归属用户可读，防止越权读取/导出他人私有模板。
     * @param template 模板内容
     */
    private void checkReadable(WorkflowTemplate template) {
        if (Boolean.TRUE.equals(template.getBuiltIn())) {
            return;
        }
        if (!Objects.equals(template.getUserId(), context.userId())) {
            throw CheckedException.forbidden("无权限查看此模板");
        }
    }

    /**
     * 转换为响应对象
     * @param template 模板内容
     * @return 处理结果
     */
    private WorkflowTemplateResp convertToResp(WorkflowTemplate template) {
        WorkflowTemplateResp resp = BeanUtil.toBean(template, WorkflowTemplateResp.class);

        // 设置分类描述
        Arrays.stream(TemplateCategory.values())
                .filter(c -> c.getCode().equals(template.getCategory()))
                .findFirst()
                .ifPresent(c -> resp.setCategoryDesc(c.getDescription()));

        // 计算节点数量
        if (template.getGraph() != null && template.getGraph().getNodes() != null) {
            resp.setNodeCount(template.getGraph().getNodes().size());
        } else {
            resp.setNodeCount(0);
        }

        return resp;
    }
}
