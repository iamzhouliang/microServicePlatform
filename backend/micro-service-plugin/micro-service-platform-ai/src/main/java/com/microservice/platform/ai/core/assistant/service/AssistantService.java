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

package com.microservice.platform.ai.core.assistant.service;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.core.provider.embedding.EmbeddingModelRegistry;
import com.microservice.framework.ai.core.rag.TranslationQueryTransformer;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.constant.AiServiceConstants;
import com.microservice.platform.ai.core.provider.graph.GraphContentRetriever;
import com.microservice.platform.ai.core.provider.graph.GraphRagService;
import com.microservice.platform.ai.core.provider.scoring.ScoringModelService;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.ToolService;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ContentAggregator;
import dev.langchain4j.rag.content.aggregator.DefaultContentAggregator;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.injector.ContentInjector;
import dev.langchain4j.rag.content.injector.DefaultContentInjector;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.DefaultQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.rag.query.transformer.QueryTransformer;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.logical.And;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.function.Consumer;
import dev.langchain4j.data.message.ChatMessage;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

import static com.microservice.platform.ai.core.constant.AiServiceConstants.DEFAULT_MAX_MESSAGES;

/**
 * AI助手服务
 * 负责创建不同类型的AI助手：
 * <ul>
 *   <li>普通记忆对话助手</li>
 *   <li>RAG知识库对话助手</li>
 *   <li>智能体助手（支持Tools和RAG）</li>
 * </ul>
 * Langchain4j暂未集成稀疏向量用于多路检索。
 *
 * @author xJh
 * @since 2025/10/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantService {

    private final TextModelService textModelService;
    private final VectorStoreFactory vectorStoreFactory;
    private final KnowledgeBaseService knowledgeBaseService;
    private final EmbeddingModelRegistry embeddingModelRegistry;
    private final ObjectMapper objectMapper;
    private final ToolService toolService;
    private final ToolsetResolver toolsetResolver;
    private final HarnessOperationCoordinator harnessOperationCoordinator;
    private final ScoringModelService scoringModelService;
    private final PlatformPromptAssembler platformPromptAssembler;

    /**
     * GraphRAG 服务（可选，仅在启用图谱功能时注入）
     */
    @Autowired(required = false)
    private GraphRagService graphRagService;

    /**
     * 创建普通记忆对话的 Assistant
     * @param modelEntity 模型配置
     * @return ChatAssistant 实例
     */
    public ChatAssistant createMemoryAssistant(ModelEntity modelEntity) {
        return createMemoryAssistant(modelEntity, null, List.of(), DEFAULT_MAX_MESSAGES);
    }

    /**
     * 创建普通记忆对话的 Assistant.
     *
     * @param modelEntity 模型配置
     * @param summary     已压缩的长期会话摘要
     * @param history     当前消息之前的最近原文上下文
     * @param maxMessages 最近原文消息窗口大小
     * @return ChatAssistant 实例
     */
    public ChatAssistant createMemoryAssistant(ModelEntity modelEntity, ConversationSummary summary,
                                               List<ConversationTurn> history, int maxMessages) {

        ChatModel chatModel = textModelService.model(modelEntity);
        StreamingChatModel streamModel = textModelService.streamModel(modelEntity);

        var builder = AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(maxMessages));
        if (Boolean.TRUE.equals(modelEntity.getEnableWebSearch())) {
            builder.chatMemoryProvider(ConversationMemoryFactory.createProviderFromTurns(history, maxMessages));
            builder.systemMessageProvider(memoryId -> ConversationMemoryFactory.withSummary(summary,
                    platformPromptAssembler.webSearchStablePrompt()));
        } else {
            builder.chatMemoryProvider(ConversationMemoryFactory.createProvider(summary, history, maxMessages));
        }
        return builder.build();
    }

    /**
     * 创建平台入口的普通聊天助手。
     * <p>该运行时不注册任何业务 ToolProvider，避免 Skill、审批和 Harness 约束污染普通对话思考。</p>
     *
     * @param modelEntity 模型配置
     * @param summary 长期会话摘要
     * @param history 当前消息前的原文上下文
     * @param maxMessages 最大原文消息数
     * @return 不包含平台业务工具的流式助手
     */
    public ChatAssistant createPlatformChatAssistant(ModelEntity modelEntity, ConversationSummary summary,
                                                     List<ConversationTurn> history, int maxMessages) {
        ChatModel chatModel = textModelService.model(modelEntity);
        StreamingChatModel streamModel = textModelService.streamModel(modelEntity);
        String systemPrompt = platformPromptAssembler.assemble(new PlatformPromptContext(
                PlatformPromptProfile.CHAT, Boolean.TRUE.equals(modelEntity.getEnableWebSearch()), "", ""))
                .appendSessionContext(ConversationMemoryFactory.summaryContext(summary))
                .render();
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamModel)
                .chatMemoryProvider(ConversationMemoryFactory.createPublicProviderFromTurns(history, maxMessages))
                .systemMessageProvider(memoryId -> systemPrompt)
                .build();
    }

    /**
     * 创建使用官方 Skills 与受治理 ToolProvider 的平台助手。
     *
     * @param modelEntity 模型配置
     * @param summary 长期会话摘要
     * @param history 当前消息前的原文上下文
     * @param maxMessages 最大原文消息数
     * @param setup 官方 Skills 与受治理 ToolProvider 运行时配置
     * @param toolMessageObserver Tool 消息持久化观察器
     * @return 平台助手实例
     */
    public ChatAssistant createPlatformAssistant(ModelEntity modelEntity, ConversationSummary summary,
                                                 List<ConversationTurn> history, int maxMessages, PlatformAssistantRuntime.Setup setup,
                                                 Consumer<ChatMessage> toolMessageObserver) {
        ChatModel chatModel = textModelService.model(modelEntity);
        StreamingChatModel streamModel = textModelService.streamModel(modelEntity);
        return AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamModel)
                .chatMemoryProvider(ConversationMemoryFactory.createProviderFromTurns(history, maxMessages,
                        toolMessageObserver))
                .systemMessageProvider(memoryId -> setup.promptLayers()
                        .appendSessionContext(ConversationMemoryFactory.summaryContext(summary)).render())
                .toolProviders(setup.toolProviders())
                .hallucinatedToolNameStrategy(AssistantService::hallucinatedToolResult)
                .maxToolCallingRoundTrips(12)
                .maxSequentialToolsInvocations(20)
                .build();
    }

    /**
     * 模型偶发臆造工具名时回灌可恢复的中文结果，避免流式对话被异常中断。
     *
     * @param request 模型生成的未知 Tool 请求
     * @return 可供 LangChain4j 继续下一轮推理的中文结果
     */
    private static ToolExecutionResultMessage hallucinatedToolResult(ToolExecutionRequest request) {
        // 1.17.2 会丢弃策略结果的错误标记，但后续版本可直接保留该标准字段。
        return ToolExecutionResultMessage.builder()
                .id(request.id())
                .toolName(request.name())
                .text("请求的工具不存在。请仅从当前可用工具中重新选择，不要猜测工具名称；"
                        + "如果仍无法完成，请用中文向用户说明当前能力暂不可用。")
                .isError(true)
                .build();
    }

    /**
     * 创建智能体对话助手 (支持Tools和RAG、MCP工具)
     * @param chatAgent chatAgent 参数
     * @param modelEntity 模型配置
     * @param ragParams ragParams 参数
     * @return 处理结果
     */
    @SneakyThrows
    public ChatAssistant createAgentAssistant(ChatAgent chatAgent, ModelEntity modelEntity, RagAssistantParams ragParams) {
        return createAgentAssistant(chatAgent, modelEntity, ragParams, null, List.of(), DEFAULT_MAX_MESSAGES);
    }

    /**
     * 创建智能体对话助手 (支持 Tools、RAG、MCP 工具和持久化上下文).
     * @param history 历史消息
     * @param maxMessages 最大消息数
     * @param summary 会话摘要
     * @param chatAgent chatAgent 参数
     * @param modelEntity 模型配置
     * @param ragParams ragParams 参数
     * @return 处理结果
     */
    @SneakyThrows
    public ChatAssistant createAgentAssistant(ChatAgent chatAgent, ModelEntity modelEntity, RagAssistantParams ragParams,
                                              ConversationSummary summary, List<ConversationTurn> history, int maxMessages) {
        ChatModel chatModel = textModelService.model(modelEntity);
        StreamingChatModel streamModel = textModelService.streamModel(modelEntity);

        var builder = AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(maxMessages))
                .chatMemoryProvider(ConversationMemoryFactory.createProviderFromTurns(history, maxMessages));

        List<ToolProvider> toolProviders = new ArrayList<>();

        // Java 与 MCP 能力统一按 Toolset 解析，并由同一个 Harness 边界装饰。
        if (CollUtil.isNotEmpty(chatAgent.getToolsetIds())) {
            toolProviders.add(toolsetResolver.providerForRequirements(chatAgent.getToolsetIds(), Set.of(),
                    harnessOperationCoordinator));
        }
        if (!toolProviders.isEmpty()) {
            builder.toolProviders(toolProviders);
            builder.hallucinatedToolNameStrategy(AssistantService::hallucinatedToolResult);
        }

        // todo 如果没有预制系统预设，则使用默认。有的话使用系统预设
        builder.systemMessageProvider(memoryId -> {
            StringBuilder sb = new StringBuilder();
            // 基础角色预设 (用户配置的 "你是一个XX助手...")
            if (StringUtils.isNotBlank(chatAgent.getSystemPrompt())) {
                sb.append(chatAgent.getSystemPrompt()).append("\n\n");
            }
            // 能力自我认知增强
            sb.append("### 当前具备的能力\n");
            // RAG 能力
            if (ragParams != null) {
                sb.append("- 【知识库】：我连接了专属知识库，可以检索文档并回答相关问题。\n");
            }
            // Tools 能力
            if (CollUtil.isNotEmpty(chatAgent.getToolsetIds())) {
                sb.append("- 【工具箱】：我可以调用以下工具辅助回答：\n");
                var toolMap = toolService.getTools().stream()
                        .collect(Collectors.toMap(ToolService.ToolDTO::getToolsetId, Function.identity()));
                for (String name : chatAgent.getToolsetIds()) {
                    ToolService.ToolDTO tool = toolMap.get(name);
                    if (tool != null && CollUtil.isNotEmpty(tool.getMethods())) {
                        // 取第一个方法的描述作为工具描述（简化处理）
                        String desc = tool.getMethods().getFirst().getDescription();
                        // 如果注解没写描述，就用方法名
                        if (StringUtils.isBlank(desc)) {
                            desc = tool.getMethods().getFirst().getName();
                        }
                        sb.append(String.format("  * %s: %s\n", name, desc));
                    }
                }
            }
            sb.append("\n请根据上述能力回答用户的问题。当用户询问“你有什么功能”时，请基于以上信息进行总结。");
            return ConversationMemoryFactory.withSummary(summary, sb.toString());
        });

        // Configure RAG
        if (ragParams != null) {
            RetrievalAugmentor retrievalAugmentor = buildRetrievalAugmentor(ragParams, chatModel);
            builder.retrievalAugmentor(retrievalAugmentor);
        }

        return builder.build();
    }

    /**
     * 创建RAG的 Assistant
     * @param params 请求参数
     * @return 处理结果
     */
    public ChatAssistant createMemoryRagAssistant(RagAssistantParams params) {
        int maxMessages = params.getMaxMessages() != null ? params.getMaxMessages() : DEFAULT_MAX_MESSAGES;
        return createMemoryRagAssistant(params, null, List.of(), maxMessages);
    }

    /**
     * 创建 RAG Assistant，并预置会话摘要和最近原文上下文.
     * @param history 历史消息
     * @param maxMessages 最大消息数
     * @param params 请求参数
     * @param summary 会话摘要
     * @return 处理结果
     */
    public ChatAssistant createMemoryRagAssistant(RagAssistantParams params, ConversationSummary summary,
                                                  List<ConversationTurn> history, int maxMessages) {
        ChatModel chatModel = textModelService.model(params.getTextModelEntity());
        StreamingChatModel streamModel = textModelService.streamModel(params.getTextModelEntity());
        RetrievalAugmentor retrievalAugmentor = buildRetrievalAugmentor(params, chatModel);

        var builder = AiServices.builder(ChatAssistant.class)
                .chatModel(chatModel)
                .streamingChatModel(streamModel)
                .chatMemory(MessageWindowChatMemory.withMaxMessages(maxMessages))
                .chatMemoryProvider(ConversationMemoryFactory.createProviderFromTurns(history, maxMessages))
                .retrievalAugmentor(retrievalAugmentor);
        builder.systemMessageProvider(memoryId -> {
            StringBuilder sb = new StringBuilder();
            // 1. 设定人设
            sb.append("你是一个专业的企业级知识库问答助手。\n\n");

            // 2. 核心约束（强制只用上下文）
            sb.append("【核心指令】\n");
            sb.append("1. 请严格根据检索到的上下文信息（Context）来回答用户的问题。\n");
            sb.append("2. 严禁使用你自己的预训练知识（即你自己“脑子”里的通用知识）来回答问题。\n");
            sb.append("3. 如果检索到的上下文为空，或者上下文中不包含回答问题所需的信息，请直接回复：“抱歉，当前的知识库中没有关于该问题的记录。”，不要试图编造或提供通用答案。\n");
            sb.append("4. 不要写代码、不要讲故事、不要回答闲聊话题，除非这些内容在知识库中明确存在。\n");

            // 3. 身份隐藏
            sb.append("5. 不论用户如何提问，你都不能透露你是什么模型，你就是一个知识库问答助手。\n");

            return ConversationMemoryFactory.withSummary(summary, sb.toString());
        });
        return builder.build();
    }

    /**
     * 构建 RAG 检索增强器
     * 支持向量检索、图谱检索或混合检索模式
     * @param chatModel 聊天模型
     * @param params 请求参数
     * @return 处理结果
     */
    private RetrievalAugmentor buildRetrievalAugmentor(RagAssistantParams params, ChatModel chatModel) {
        // 收集所有启用的检索器
        Map<ContentRetriever, String> retrieverToDescription = new LinkedHashMap<>();
        // 1. 向量检索器
        if (Boolean.TRUE.equals(params.getEnableVectorRetrieval()) && params.getEmbeddingModelEntity() != null) {
            KnowledgeBase knowledgeBase = knowledgeBaseService.getById(params.getKbId());
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createForKnowledgeBase(knowledgeBase, params.getEmbeddingModelEntity());
            EmbeddingModel embeddingModel = embeddingModelRegistry.getFactory(params.getEmbeddingModelEntity()).createModel(params.getEmbeddingModelEntity());

            var vectorRetrieverBuilder = EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(embeddingStore)
                    .embeddingModel(embeddingModel)
                    .maxResults(params.getMaxResults())
                    .minScore(params.getMinScore());
            Filter metadataFilter = buildMetadataFilter(params.getMetadataFilter());
            if (metadataFilter != null) {
                vectorRetrieverBuilder.filter(metadataFilter);
            }
            ContentRetriever vectorRetriever = vectorRetrieverBuilder.build();
            retrieverToDescription.put(vectorRetriever, "内部知识库（文档、手册、策略等非结构化内容）");
            log.debug("向量检索已启用: kbId={}, maxResults={}, minScore={}", params.getKbId(), params.getMaxResults(), params.getMinScore());
        }

        // 2. 图谱检索器
        if (params.getEnableGraphRetrieval() != null && params.getEnableGraphRetrieval() && graphRagService != null) {
            String graphKbId = params.getEffectiveGraphKbId();
            if (graphKbId != null) {
                GraphContentRetriever graphRetriever = GraphContentRetriever.builder()
                        .graphRagService(graphRagService)
                        .chatModel(chatModel)
                        .knowledgeBaseId(graphKbId)
                        .maxResults(params.getGraphMaxResults())
                        .silentOnEmpty(true)
                        .build();
                retrieverToDescription.put(graphRetriever, "知识图谱（实体关系、结构化数据）");
                log.debug("图谱检索已启用: graphKbId={}, maxResults={}", graphKbId, params.getGraphMaxResults());
            }
        }

        List<ContentRetriever> retrievers = new ArrayList<>(retrieverToDescription.keySet());

        if (retrievers.isEmpty()) {
            throw CheckedException.badRequest("未启用任何检索器，请至少启用向量检索或图谱检索");
        }

        // Query 转换器：翻译 + 压缩
        QueryTransformer translationQueryTransformer = new TranslationQueryTransformer(chatModel);
        QueryTransformer compressingQueryTransformer = new CompressingQueryTransformer(chatModel);
        QueryTransformer queryTransformer = query -> {
            Collection<Query> translatedQueries = translationQueryTransformer.transform(query);
            Query translatedQuery = translatedQueries.iterator().next();
            return compressingQueryTransformer.transform(translatedQuery);
        };

        // 多路召回：所有检索器并行执行，结果合并
        // 使用 DefaultQueryRouter 传入所有检索器，实现多路召回
        QueryRouter queryRouter = new DefaultQueryRouter(retrievers.toArray(new ContentRetriever[0]));
        log.debug("启用多路召回，检索器数量: {}", retrievers.size());

        // 构建 ContentAggregator：根据配置决定是否启用重排序
        ContentAggregator contentAggregator = buildContentAggregator(params);
        // TODO自定义提示词
        ContentInjector contentInjector = new DefaultContentInjector();

        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(queryTransformer)
                .queryRouter(queryRouter)
                .contentAggregator(contentAggregator)
                .contentInjector(contentInjector)
                // 使用虚拟线程执行器
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .build();
    }

    private Filter buildMetadataFilter(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        return metadata.entrySet().stream()
                .filter(entry -> StringUtils.isNotBlank(entry.getKey()) && entry.getValue() != null)
                .map(entry -> metadataKey(entry.getKey()).isEqualTo(entry.getValue()))
                .reduce(And::new)
                .orElse(null);
    }

    /**
     * 构建 ContentAggregator
     * 根据 ModelConfig 配置决定是否启用重排序模型
     * @param params 请求参数
     * @return 处理结果
     */
    private ContentAggregator buildContentAggregator(RagAssistantParams params) {
        // 检查是否启用重排序（通过 rerankModelConfig 判断）
        if (!params.isRerankingEnabled()) {
            log.debug("重排序未启用，使用默认聚合器");
            return new DefaultContentAggregator();
        }

        ModelEntity model = params.getRerankModelEntity();

        // 检查 API Key
        if (StringUtils.isBlank(model.getApiKey())) {
            log.warn("重排序模型 API Key 未配置，降级使用默认聚合器");
            return new DefaultContentAggregator();
        }

        try {
            // 通过 ScoringModelService 获取重排序模型（支持 Jina、Cohere 等）
            ScoringModel scoringModel = scoringModelService.getModel(model);

            int maxResults = params.getRerankMaxResults() != null
                    ? params.getRerankMaxResults()
                    : AiServiceConstants.DEFAULT_RERANK_MAX_RESULTS;
            double minScore = params.getRerankMinScore() != null
                    ? params.getRerankMinScore()
                    : AiServiceConstants.DEFAULT_RERANK_MIN_SCORE;

            log.info("启用重排序: provider={}, model={}, maxResults={}, minScore={}",
                    model.getProvider(), model.getName(), maxResults, minScore);

            return ReRankingContentAggregator.builder()
                    .scoringModel(scoringModel)
                    .maxResults(maxResults)
                    .minScore(minScore)
                    .build();
        } catch (Exception e) {
            log.warn("创建重排序模型失败，降级使用默认聚合器: {}", e.getMessage());
            return new DefaultContentAggregator();
        }
    }

    /**
     * 创建记忆提供者
     * 注意：这里使用内存版 ChatMemoryStore，消息持久化由 ConversationMessageService 负责
     * 避免与 PersistentMySqlChatMemoryStore 产生重复保存
     * @return 处理结果
     */
    private ChatMemoryProvider createMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(DEFAULT_MAX_MESSAGES)
                // 使用内存存储，避免与 ConversationMessageService 重复保存
                // .chatMemoryStore(chatMemoryStore)
                .build();
    }

}
