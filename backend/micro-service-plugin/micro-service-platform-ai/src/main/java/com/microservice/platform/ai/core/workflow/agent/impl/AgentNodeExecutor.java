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

import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.assistant.service.AssistantService;
import com.microservice.platform.ai.core.assistant.service.RagAssistantParams;
import com.microservice.platform.ai.core.constant.AiServiceConstants;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.AgentNodeConfig;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import com.microservice.platform.ai.service.ChatAgentService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 智能体节点执行器
 * 复用现有的 ChatAgent 配置和 AssistantService.createAgentAssistant()
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentNodeExecutor extends AbstractNodeExecutor {

    private final ChatAgentService chatAgentService;
    private final AssistantService assistantService;
    private final ModelConfigRetriever modelConfigRetriever;
    private final KnowledgeBaseService knowledgeBaseService;

    @Override
    public NodeType getType() {
        return NodeType.AGENT;
    }

    @Override
    public void validate(WorkflowNode node) {
        AgentNodeConfig config = parseConfig(node, AgentNodeConfig.class);
        if (config.getAgentId() == null) {
            throw new IllegalArgumentException("智能体节点缺少 agentId 配置");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行智能体节点: {}", node.getId());

        AgentNodeConfig config = parseConfig(node, AgentNodeConfig.class);
        Long agentId = config.getAgentId();
        ChatAgent chatAgent = chatAgentService.getById(agentId);
        if (chatAgent == null) {
            return NodeExecutionResult.failure("智能体不存在: " + agentId);
        }

        // 获取输入内容
        String input = resolveTemplate(config.getInput(), context);
        if (input == null || input.isEmpty()) {
            return NodeExecutionResult.failure("智能体节点输入为空");
        }

        try {
            // 获取模型配置
            ModelEntity textModel = modelConfigRetriever.getRequiredModel(chatAgent.getModelId(), ModelType.TEXT);

            // 构建 RAG 参数（如果智能体关联了知识库）
            RagAssistantParams ragParams = null;
            if (chatAgent.getKbId() != null) {
                KnowledgeBase kb = knowledgeBaseService.getById(chatAgent.getKbId());
                if (kb != null) {
                    ModelEntity embeddingModel = modelConfigRetriever.getModelByIdAndType(
                            kb.getEmbedModelId(), ModelType.EMBEDDING);
                    ModelEntity rerankModel = null;
                    if (kb.getRerankModelId() != null) {
                        rerankModel = modelConfigRetriever.getModel(kb.getRerankModelId()).orElse(null);
                    }

                    ragParams = RagAssistantParams.builder()
                            .kbId(chatAgent.getKbId())
                            .textModelEntity(textModel)
                            .embeddingModelEntity(embeddingModel)
                            .rerankModelEntity(rerankModel)
                            .enableGraphRetrieval(kb.getEnableGraph())
                            .build();
                }
            }

            // 创建智能体助手
            ChatAssistant assistant = assistantService.createAgentAssistant(chatAgent, textModel, ragParams);

            // 执行对话（使用 executionId 作为 memoryId，无图片）
            // 使用 CompletableFuture 等待流式响应完成
            java.util.concurrent.CompletableFuture<String> responseFuture = new java.util.concurrent.CompletableFuture<>();
            StringBuilder responseBuilder = new StringBuilder();

            dev.langchain4j.service.TokenStream tokenStream = assistant.chat(
                    // 转换为正数 Long
                    context.getExecutionId().hashCode() & 0x7FFFFFFFL,
                    input,
                    java.util.Collections.emptyList());

            tokenStream.onPartialResponse(token -> responseBuilder.append(token))
                    .onCompleteResponse(response -> responseFuture.complete(responseBuilder.toString()))
                    .onError(responseFuture::completeExceptionally)
                    .start();

            String response = responseFuture.get(
                    AiServiceConstants.AGENT_RESPONSE_TIMEOUT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);

            // 构建输出
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", response);
            outputs.put("input", input);
            outputs.put("agentId", agentId);
            outputs.put("agentName", chatAgent.getName());
            if (config.getOutputVariable() != null && !config.getOutputVariable().isBlank()) {
                outputs.put(config.getOutputVariable(), response);
                context.setVariable(config.getOutputVariable(), response);
            }

            log.debug("智能体节点 {} 执行完成，响应长度: {}", node.getId(), response.length());
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("智能体节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("智能体执行失败: " + e.getMessage());
        }
    }
}
