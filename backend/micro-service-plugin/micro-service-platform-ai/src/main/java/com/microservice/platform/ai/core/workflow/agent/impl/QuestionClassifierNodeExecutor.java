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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.QuestionClassifierConfig;
import com.microservice.platform.ai.core.workflow.config.node.QuestionClassifierConfig.ClassCategory;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 问题分类器节点执行器
 * 使用 LLM 对输入文本进行智能分类，路由到不同的处理分支。
 * 每个分类类别对应一个输出端口，根据分类结果选择对应的分支继续执行。
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionClassifierNodeExecutor extends AbstractNodeExecutor {

    /**
     * 默认分类系统提示词
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            你是问题分类器。你的任务是把用户输入准确归类到一个预定义类别。

            规则:
            1. 只能返回类别 ID，不能返回其他内容。
            2. 不要包含解释、标点或额外文本。
            3. 如果输入不明确匹配任何类别，选择最相关的类别。
            4. 响应必须是单个类别 ID。
            """;

    private final TextModelService textModelService;
    private final ModelConfigRetriever modelConfigRetriever;

    @Override
    public NodeType getType() {
        return NodeType.QUESTION_CLASSIFIER;
    }

    @Override
    public void validate(WorkflowNode node) {
        QuestionClassifierConfig config = parseConfig(node, QuestionClassifierConfig.class);

        if (config.getModelId() == null) {
            throw new IllegalArgumentException("问题分类器节点缺少 modelId 配置");
        }

        if (config.getCategories() == null || config.getCategories().isEmpty()) {
            throw new IllegalArgumentException("问题分类器节点至少需要一个分类类别");
        }

        // 验证每个类别都有 ID
        for (int i = 0; i < config.getCategories().size(); i++) {
            ClassCategory category = config.getCategories().get(i);
            if (category.getId() == null || category.getId().trim().isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个分类缺少 ID");
            }
            if (category.getName() == null || category.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个分类缺少名称");
            }
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行问题分类器节点: {}", node.getId());

        // 解析强类型配置
        QuestionClassifierConfig config = parseConfig(node, QuestionClassifierConfig.class);

        // 获取输入文本
        String inputVariable = config.getInputVariable();
        String inputText;
        if (inputVariable != null && !inputVariable.isEmpty()) {
            inputText = resolveTemplate(inputVariable, context);
        } else {
            // 尝试从默认输入获取
            inputText = context.getVariable("input", String.class);
        }

        if (inputText == null || inputText.trim().isEmpty()) {
            return NodeExecutionResult.failure("问题分类器节点输入文本为空");
        }

        // 获取模型
        final ModelEntity modelEntity = modelConfigRetriever.getRequiredModel(config.getModelId());

        // 构建分类提示词
        String classificationPrompt = buildClassificationPrompt(inputText, config);

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // 使用自定义提示词或默认提示词
        String systemPrompt;
        if (config.isAdvancedMode() && config.getCustomPromptTemplate() != null) {
            systemPrompt = resolveTemplate(config.getCustomPromptTemplate(), context);
        } else {
            systemPrompt = DEFAULT_SYSTEM_PROMPT;
        }
        messages.add(SystemMessage.from(systemPrompt));
        messages.add(UserMessage.from(classificationPrompt));

        // 调用 LLM 进行分类
        ChatModel chatModel = textModelService.model(modelEntity);
        ChatResponse response = chatModel.chat(messages);

        // 提取响应内容
        AiMessage aiMessage = response.aiMessage();
        String rawResponse = aiMessage.text().trim();

        // 解析分类结果
        String selectedCategoryId = parseClassificationResult(rawResponse, config.getCategories());
        ClassCategory selectedCategory = findCategoryById(selectedCategoryId, config.getCategories());

        if (selectedCategory == null) {
            return NodeExecutionResult.failure("问题分类器返回未知类别: " + rawResponse);
        }

        // 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("category", selectedCategoryId);
        outputs.put("categoryName", selectedCategory.getName());
        outputs.put("categoryDescription", selectedCategory.getDescription());
        outputs.put("rawResponse", rawResponse);
        outputs.put("inputText", inputText);

        // 添加 token 使用信息
        if (response.tokenUsage() != null) {
            outputs.put("inputTokens", response.tokenUsage().inputTokenCount());
            outputs.put("outputTokens", response.tokenUsage().outputTokenCount());
            outputs.put("totalTokens", response.tokenUsage().totalTokenCount());
        }

        log.debug("问题分类器节点 {} 将输入分类为: {} ({})",
                node.getId(), selectedCategoryId, selectedCategory.getName());

        // 返回结果，设置下一个分支为选中的类别 ID
        return NodeExecutionResult.builder()
                .success(true)
                .outputs(outputs)
                .nextBranch(selectedCategoryId)
                .build();
    }

    /**
     * 构建分类提示词
     * @param config 节点配置
     * @param inputText inputText 参数
     * @return 处理结果
     */
    private String buildClassificationPrompt(String inputText, QuestionClassifierConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("请将下面的输入归类到以下类别之一:\n\n");

        // 添加类别列表
        sb.append("类别:\n");
        for (ClassCategory category : config.getCategories()) {
            sb.append("- ID: ").append(category.getId());
            sb.append(" | 名称: ").append(category.getName());
            if (category.getDescription() != null && !category.getDescription().isEmpty()) {
                sb.append(" | 描述: ").append(category.getDescription());
            }
            sb.append("\n");

            // 添加示例（如果有）
            if (category.getExamples() != null && !category.getExamples().isEmpty()) {
                sb.append("  示例: ");
                sb.append(String.join(", ", category.getExamples()));
                sb.append("\n");
            }
        }

        // 添加额外指导说明
        if (config.getInstructions() != null && !config.getInstructions().isEmpty()) {
            sb.append("\n额外说明:\n");
            sb.append(config.getInstructions());
            sb.append("\n");
        }

        sb.append("\n---\n");
        sb.append("待分类输入:\n");
        sb.append(inputText);
        sb.append("\n---\n");
        sb.append("\n只能返回上方类别列表中的一个类别 ID:");

        return sb.toString();
    }

    /**
     * 解析分类结果
     * 尝试从 LLM 响应中提取有效的类别 ID
     * @param categories categories 参数
     * @param response response 参数
     * @return 处理结果
     */
    private String parseClassificationResult(String response, List<ClassCategory> categories) {
        if (response == null || response.isEmpty()) {
            return null;
        }

        // 清理响应
        String cleaned = response.trim()
                .toLowerCase()
                // 移除引号
                .replaceAll("[\"'`]", "")
                // 移除前缀
                .replaceAll("^(category[:\\s]*|id[:\\s]*)", "")
                .trim();

        // 获取所有类别 ID（小写）
        Map<String, String> categoryIdMap = categories.stream()
                .collect(Collectors.toMap(
                        c -> c.getId().toLowerCase(),
                        ClassCategory::getId));

        // 直接匹配
        if (categoryIdMap.containsKey(cleaned)) {
            return categoryIdMap.get(cleaned);
        }

        // 尝试匹配类别名称
        for (ClassCategory category : categories) {
            if (category.getName().toLowerCase().equals(cleaned)) {
                return category.getId();
            }
        }

        // 尝试部分匹配（响应包含类别 ID）
        for (Map.Entry<String, String> entry : categoryIdMap.entrySet()) {
            if (cleaned.contains(entry.getKey())) {
                return entry.getValue();
            }
        }

        // 尝试部分匹配（响应包含类别名称）
        for (ClassCategory category : categories) {
            if (cleaned.contains(category.getName().toLowerCase())) {
                return category.getId();
            }
        }

        // 无法匹配，返回原始响应（可能是有效的 ID）
        return response.trim();
    }

    /**
     * 根据 ID 查找类别
     * @param categories categories 参数
     * @param categoryId categoryId 参数
     * @return 处理结果
     */
    private ClassCategory findCategoryById(String categoryId, List<ClassCategory> categories) {
        if (categoryId == null) {
            return null;
        }

        return categories.stream()
                .filter(c -> c.getId().equalsIgnoreCase(categoryId))
                .findFirst()
                .orElse(null);
    }

    /**
     * 获取所有类别 ID 列表
     * 用于前端生成输出端口
     * @param config 节点配置
     * @return 处理结果
     */
    public List<String> getCategoryIds(QuestionClassifierConfig config) {
        if (config == null || config.getCategories() == null) {
            return new ArrayList<>();
        }
        return config.getCategories().stream()
                .map(ClassCategory::getId)
                .collect(Collectors.toList());
    }
}
