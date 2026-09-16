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

import cn.hutool.core.util.StrUtil;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.ai.harness.memory.ApproximateTokenEstimator;
import com.microservice.framework.ai.harness.memory.ContextBudgetPlanner;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorizationProvider;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.framework.redis.plus.lock.RedisLockHelper;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.assistant.routing.PlatformConversationRoute;
import com.microservice.platform.ai.core.assistant.routing.PlatformConversationRouter;
import com.microservice.platform.ai.core.assistant.service.AssistantService;
import com.microservice.platform.ai.core.assistant.service.ConversationMemoryFactory;
import com.microservice.platform.ai.core.assistant.service.RagAssistantParams;
import com.microservice.platform.ai.core.assistant.service.PlatformAssistantRuntime;
import com.microservice.platform.ai.core.config.ConversationContextProperties;
import com.microservice.platform.ai.core.constant.AiServiceConstants;
import com.microservice.platform.ai.core.enums.ConversationType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.sse.SseChatHelper;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.domain.dto.req.AssistantMessageSaveReq;
import com.microservice.platform.ai.domain.dto.req.UserMessageSaveReq;
import com.microservice.platform.ai.domain.dto.result.ChatCompletionResult;
import com.microservice.platform.ai.domain.entity.*;
import com.microservice.platform.ai.service.*;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.invocation.InvocationParameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 对话服务实现
 * 处理不同类型的对话请求：普通文本对话、知识库对话（RAG）、智能体对话
 *
 * @author xJh
 * @since 2025/10/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private static final String CONTEXT_WINDOW_VARIABLE = "context_window_tokens";
    private static final String MAX_OUTPUT_VARIABLE = "max_tokens";
    private static final ApproximateTokenEstimator TOKEN_ESTIMATOR = new ApproximateTokenEstimator();
    private static final ContextBudgetPlanner BUDGET_PLANNER = new ContextBudgetPlanner();

    private final AuthenticationContext context;
    private final SseChatHelper sseChatHelper;
    private final ModelConfigRetriever modelConfigRetriever;
    private final AssistantService assistantService;
    private final ConversationMessageService conversationMessageService;
    private final ConversationSummaryService conversationSummaryService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ChatAgentService chatAgentService;
    private final ConversationService conversationService;
    private final ConversationContextProperties contextProperties;
    private final RedisLockHelper redisLockHelper;
    private final PlatformAssistantRuntime platformAssistantRuntime;
    private final PlatformConversationRouter platformConversationRouter;
    private final HarnessOperationStore harnessOperationStore;
    private final DelegatedAuthorizationProvider delegatedAuthorizationProvider;
    private final Environment environment;

    @Override
    public SseEmitter chatStream(AskReq askReq) {
        String reservationToken = null;
        // 防止重复请求
        if (askReq.getConversationId() != null) {
            reservationToken = sseChatHelper.tryReserveConversation(String.valueOf(askReq.getConversationId()));
            if (reservationToken == null) {
                SseEmitter emitter = new SseEmitter(0L);
                try {
                    emitter.send(SseEmitter.event().name("error").data("请求过于频繁，请稍后再试"));
                    emitter.complete();
                } catch (Exception e) {
                    log.debug("发送限流提示失败", e);
                }
                return emitter;
            }
        }

        log.info("开始处理对话请求: chatType={}, userId={}", askReq.getChatType(), context.userId());
        String traceId = context.userId() + "-" + UUID.randomUUID();
        SseEmitter emitter = sseChatHelper.createEmitter(traceId);
        try {
            switch (askReq.getChatType()) {
                case NORMAL_TEXT -> handleTextChat(askReq, emitter, reservationToken);
                case KNOWLEDGE_BASE -> handleKnowledgeChat(askReq, emitter, reservationToken);
                case GENERAL_AGENT, PLATFORM_AGENT -> handleAgentChat(askReq, emitter, reservationToken);
                default -> throw CheckedException.badRequest(
                        String.format(AiServiceConstants.ERROR_UNSUPPORTED_CHAT_TYPE, askReq.getChatType()));
            }
        } catch (Exception e) {
            log.error("对话初始化失败: chatType={}, conversationId={}",
                    askReq.getChatType(), askReq.getConversationId(), e);
            if (askReq.getConversationId() != null && reservationToken != null) {
                sseChatHelper.markConversationComplete(String.valueOf(askReq.getConversationId()), reservationToken);
            }
            handleChatError(emitter, "对话初始化失败: " + e.getMessage());
        }
        return emitter;
    }

    /**
     * 处理普通文本对话
     * @param askReq askReq 参数
     * @param reservationToken reservationToken 参数
     * @param sseEmitter sseEmitter 参数
     */
    private void handleTextChat(AskReq askReq, SseEmitter sseEmitter, String reservationToken) {
        log.debug("处理普通文本对话: conversationId={}, modelId={}", askReq.getConversationId(), askReq.getModelId());

        Long userId = context.userId();
        Long tenantId = context.tenantId();
        Long conversationId = askReq.getConversationId();
        String userPrompt = askReq.getPrompt();

        // 归属校验：普通对话必须校验会话归属，防止横向越权读取/写入他人会话（否则可注入他人历史并经 SSE 回流）
        assertConversationOwnership(conversationId, userId);

        // 保存用户消息
        UserMessageSaveReq userMsgReq = UserMessageSaveReq.builder()
                .conversationId(conversationId)
                .userId(userId)
                .tenantId(tenantId)
                .rawContent(userPrompt)
                .promptContent(userPrompt)
                .promptTokens(0)
                .build();
        ConversationTurn conversationTurn = conversationMessageService.saveUserMessage(userMsgReq);

        try {
            // 获取模型配置
            ModelEntity modelEntity = modelConfigRetriever.getRequiredModel(Long.valueOf(askReq.getModelId()));
            modelEntity.setReturnThinking(askReq.getReturnThinking());
            modelEntity.setEnableWebSearch(askReq.getEnableWebSearch());

            ConversationSummary summary = conversationSummaryService.getActiveSummary(conversationId);
            List<ConversationTurn> history = conversationMessageService.listContextTurns(conversationId,
                    conversationTurn.getSequenceNum(), contextProperties.getRecentRounds());
            history = excludeSummarizedTurns(summary, history);
            history = selectBudgetedHistory(modelEntity, summary, userPrompt, history,
                    contextProperties.recentMessages(), contextProperties.getBudget().getNormalSystemPromptTokens(),
                    0);

            // 创建助手并执行对话
            ChatAssistant assistant = assistantService.createMemoryAssistant(modelEntity, summary, history,
                    contextProperties.recentMessages());
            TokenStream tokenStream = assistant.chatStream(conversationId, userPrompt);

            // 处理流式响应
            sseChatHelper.chatStreamToSse(askReq, sseEmitter, tokenStream, reservationToken, result -> {
                saveAssistantMessage(conversationId, userId, tenantId, modelEntity, conversationTurn.getId(), result);
                refreshSummary(conversationId, conversationTurn.getSequenceNum());
            });
        } catch (Exception e) {
            // 同步阶段异常必须关闭 emitter，否则连接悬挂
            log.error("普通对话失败: conversationId={}", conversationId, e);
            sseChatHelper.markConversationComplete(String.valueOf(conversationId), reservationToken);
            handleChatError(sseEmitter, "对话失败: " + e.getMessage());
        }
    }

    /**
     * 处理知识库对话（RAG）
     * @param askReq askReq 参数
     * @param reservationToken reservationToken 参数
     * @param sseEmitter sseEmitter 参数
     */
    private void handleKnowledgeChat(AskReq askReq, SseEmitter sseEmitter, String reservationToken) {
        log.debug("处理知识库对话: kbId={}", askReq.getKbId());

        Long userId = context.userId();
        Long tenantId = context.tenantId();
        String userPrompt = askReq.getPrompt();
        boolean requiresDuplicateCheck = askReq.getConversationId() == null;

        // 获取或创建会话
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(askReq.getKbId());
        if (knowledgeBase == null) {
            if (askReq.getConversationId() != null && reservationToken != null) {
                sseChatHelper.markConversationComplete(String.valueOf(askReq.getConversationId()), reservationToken);
            }
            handleChatError(sseEmitter, "知识库不存在");
            return;
        }
        Conversation conversation = getOrCreateConversation(userId, askReq, ConversationType.KNOWLEDGE_BASE);
        Long conversationId = conversation.getId();
        askReq.setConversationId(conversationId);
        String activeReservationToken = requiresDuplicateCheck
                ? sseChatHelper.tryReserveConversation(String.valueOf(conversationId))
                : reservationToken;
        if (activeReservationToken == null) {
            handleChatError(sseEmitter, "请求过于频繁，请稍后再试");
            return;
        }

        try {
            // 保存用户消息
            UserMessageSaveReq userMsgReq = UserMessageSaveReq.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .tenantId(tenantId)
                    .rawContent(userPrompt)
                    .promptContent(userPrompt)
                    .promptTokens(0)
                    .build();
            ConversationTurn conversationTurn = conversationMessageService.saveUserMessage(userMsgReq);

            // 获取模型配置
            ModelEntity textModelEntity = modelConfigRetriever.getModelByIdAndType(
                    knowledgeBase.getChatModelId(), ModelType.TEXT);
            textModelEntity.setEnableWebSearch(askReq.getEnableWebSearch());
            textModelEntity.setReturnThinking(askReq.getReturnThinking());

            ModelEntity embeddingModelEntity = modelConfigRetriever.getModelByIdAndType(
                    knowledgeBase.getEmbedModelId(), ModelType.EMBEDDING);

            // 获取重排序模型配置（可选）
            ModelEntity rerankModelEntity = modelConfigRetriever.getModel(knowledgeBase.getRerankModelId())
                    .orElse(null);

            // 构建RAG参数并创建助手
            RagAssistantParams params = RagAssistantParams.builder()
                    .kbId(askReq.getKbId())
                    .textModelEntity(textModelEntity)
                    .embeddingModelEntity(embeddingModelEntity)
                    .maxResults(knowledgeBase.getTopK())
                    .minScore(knowledgeBase.getScoreThreshold())
                    .metadataFilter(askReq.getMetadataFilter())
                    .rerankModelEntity(rerankModelEntity)
                    .enableGraphRetrieval(knowledgeBase.getEnableGraph())
                    .build();

            ConversationSummary summary = conversationSummaryService.getActiveSummary(conversationId);
            List<ConversationTurn> history = conversationMessageService.listContextTurns(conversationId,
                    conversationTurn.getSequenceNum(), contextProperties.getRagRecentRounds());
            history = excludeSummarizedTurns(summary, history);
            history = selectBudgetedHistory(textModelEntity, summary, userPrompt, history,
                    contextProperties.ragRecentMessages(), contextProperties.getBudget().getRagSystemPromptTokens(),
                    contextProperties.getBudget().getRagRetrievalReserveTokens());
            ChatAssistant assistant = assistantService.createMemoryRagAssistant(params, summary, history,
                    contextProperties.ragRecentMessages());
            TokenStream tokenStream = assistant.chatStream(conversationId, userPrompt);

            // 处理流式响应
            sseChatHelper.chatStreamToSse(askReq, sseEmitter, tokenStream, activeReservationToken, result -> {
                saveAssistantMessage(conversationId, userId, tenantId, textModelEntity, conversationTurn.getId(),
                        result);
                refreshSummary(conversationId, conversationTurn.getSequenceNum());
            });

        } catch (Exception e) {
            log.error("知识库对话失败: kbId={}, query={}", askReq.getKbId(), userPrompt, e);
            sseChatHelper.markConversationComplete(String.valueOf(conversationId), activeReservationToken);
            handleChatError(sseEmitter, "知识库对话失败: " + e.getMessage());
        }
    }

    /**
     * 处理智能体对话
     * @param askReq askReq 参数
     * @param reservationToken reservationToken 参数
     * @param sseEmitter sseEmitter 参数
     */
    private void handleAgentChat(AskReq askReq, SseEmitter sseEmitter, String reservationToken) {
        log.debug("处理智能体对话: agentId={}", askReq.getAgentId());

        Long userId = context.userId();
        Long tenantId = context.tenantId();
        String userPrompt = askReq.getPrompt();
        boolean requiresDuplicateCheck = askReq.getConversationId() == null;

        // 获取或创建会话
        ConversationType conversationType = askReq.getChatType() == ConversationType.PLATFORM_AGENT
                ? ConversationType.PLATFORM_AGENT
                : ConversationType.GENERAL_AGENT;
        Conversation conversation = getOrCreateConversation(userId, askReq, conversationType);
        Long conversationId = conversation.getId();
        askReq.setConversationId(conversationId);
        String activeReservationToken = requiresDuplicateCheck
                ? sseChatHelper.tryReserveConversation(String.valueOf(conversationId))
                : reservationToken;
        if (activeReservationToken == null) {
            handleChatError(sseEmitter, "请求过于频繁，请稍后再试");
            return;
        }

        try {
            // 保存用户消息
            UserMessageSaveReq userMsgReq = UserMessageSaveReq.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .tenantId(tenantId)
                    .rawContent(userPrompt)
                    .promptContent(userPrompt)
                    .promptTokens(0)
                    .build();
            ConversationTurn conversationTurn = conversationMessageService.saveUserMessage(userMsgReq);

            // 平台助手使用系统内置配置；用户创建的智能体继续沿用数据库配置。
            ChatAgent chatAgent = platformChatAgent(askReq);

            // 获取文本模型配置
            ModelEntity textModelEntity = modelConfigRetriever.getRequiredModel(
                    chatAgent.getModelId(), ModelType.TEXT);
            textModelEntity.setReturnThinking(askReq.getReturnThinking());
            textModelEntity.setEnableWebSearch(askReq.getEnableWebSearch());

            // 构建RAG参数（如果智能体关联了知识库）
            RagAssistantParams ragParams = buildRagParamsForAgent(chatAgent, textModelEntity);

            ConversationSummary summary = conversationSummaryService.getActiveSummary(conversationId);
            List<ConversationTurn> history = conversationMessageService.listContextTurns(conversationId,
                    conversationTurn.getSequenceNum(), contextProperties.getAgentRecentRounds());
            history = excludeSummarizedTurns(summary, history);

            ChatAssistant assistant;
            TokenStream tokenStream;
            SseChatHelper.ThinkingPolicy thinkingPolicy = SseChatHelper.ThinkingPolicy.DIRECT;
            HarnessInvocation harnessInvocation = createHarnessInvocation(conversationId, conversationTurn);
            if (askReq.getChatType() == ConversationType.PLATFORM_AGENT) {
                ToolsetSelectionContext routingSelection = createToolsetSelectionContext(harnessInvocation,
                        "BUSINESS", "PLATFORM_ROUTING", Set.of());
                String capabilityIndex = platformAssistantRuntime.routingCapabilityIndex(routingSelection);
                PlatformConversationRoute route = platformConversationRouter.route(textModelEntity, userPrompt,
                        summary, history, capabilityIndex, harnessInvocation.resumeOperation());
                if (route == PlatformConversationRoute.CHAT) {
                    history = selectBudgetedHistory(textModelEntity, summary, userPrompt, history,
                            contextProperties.recentMessages(),
                            contextProperties.getBudget().getNormalSystemPromptTokens(), 0);
                    assistant = assistantService.createPlatformChatAssistant(textModelEntity, summary, history,
                            contextProperties.recentMessages());
                    tokenStream = assistant.chatStream(conversationId, userPrompt);
                } else {
                    thinkingPolicy = SseChatHelper.ThinkingPolicy.BUSINESS_SAFE;
                    ToolsetSelectionContext businessSelection = createToolsetSelectionContext(harnessInvocation,
                            "BUSINESS", "PLATFORM_ASSISTANT", Set.of());
                    PlatformAssistantRuntime.Setup setup = platformAssistantRuntime.create(harnessInvocation);
                    InvocationParameters parameters = invocationParameters(harnessInvocation, businessSelection);
                    history = selectBudgetedHistory(textModelEntity, summary, userPrompt, history,
                            contextProperties.agentRecentMessages(),
                            contextProperties.getBudget().getAgentSystemPromptTokens(),
                            contextProperties.getBudget().getAgentToolSchemaTokens());
                    assistant = assistantService.createPlatformAssistant(textModelEntity, summary, history,
                            contextProperties.agentRecentMessages(), setup,
                            message -> conversationMessageService.saveToolLoopMessage(conversationId, userId,
                                    tenantId, message));
                    tokenStream = assistant.chatStreamWithContext(conversationId, userPrompt, parameters);
                }
            } else {
                ToolsetSelectionContext customSelection = createToolsetSelectionContext(harnessInvocation,
                        "BUSINESS", "CUSTOM_AGENT", Set.copyOf(chatAgent.getToolsetIds() == null
                                ? List.of()
                                : chatAgent.getToolsetIds()));
                InvocationParameters parameters = invocationParameters(harnessInvocation, customSelection);
                history = selectBudgetedHistory(textModelEntity, summary, userPrompt, history,
                        contextProperties.agentRecentMessages(),
                        contextProperties.getBudget().getAgentSystemPromptTokens(),
                        contextProperties.getBudget().getAgentToolSchemaTokens());
                assistant = assistantService.createAgentAssistant(chatAgent, textModelEntity, ragParams,
                        summary, history, contextProperties.agentRecentMessages());
                tokenStream = assistant.chatStreamWithContext(conversationId, userPrompt, parameters);
            }

            // 业务 Tool Loop 的思考先经过安全投影，普通聊天保持真实逐段透传。
            Consumer<ChatCompletionResult> completionHandler = result -> {
                saveAssistantMessage(conversationId, userId, tenantId, textModelEntity, conversationTurn.getId(),
                        result);
                refreshSummary(conversationId, conversationTurn.getSequenceNum());
            };
            if (thinkingPolicy == SseChatHelper.ThinkingPolicy.BUSINESS_SAFE) {
                sseChatHelper.chatStreamToSse(askReq, sseEmitter, tokenStream, activeReservationToken,
                        thinkingPolicy, completionHandler);
            } else {
                sseChatHelper.chatStreamToSse(askReq, sseEmitter, tokenStream, activeReservationToken,
                        completionHandler);
            }
        } catch (Exception e) {
            // 同步阶段异常必须关闭 emitter，否则连接悬挂
            log.error("智能体对话失败: agentId={}", askReq.getAgentId(), e);
            sseChatHelper.markConversationComplete(String.valueOf(conversationId), activeReservationToken);
            handleChatError(sseEmitter, "智能体对话失败: " + e.getMessage());
        }
    }

    /**
     * 获取或创建会话
     * @param askReq askReq 参数
     * @param type 类型
     * @param userId 用户标识
     * @return 处理结果
     */
    private Conversation getOrCreateConversation(Long userId, AskReq askReq, ConversationType type) {
        String lockKey = "ai:conversation:create:%s:%s:%s:%s:%s".formatted(
                context.tenantId(), userId, type, askReq.getKbId(), askReq.getAgentId());
        return redisLockHelper.execute(lockKey, 5L, TimeUnit.SECONDS,
                () -> findOrCreateConversation(userId, askReq, type));
    }

    private HarnessInvocation createHarnessInvocation(Long conversationId, ConversationTurn turn) {
        String tenantId = String.valueOf(context.tenantId());
        String userId = String.valueOf(context.userId());
        String conversation = String.valueOf(conversationId);
        String turnId = turn.getId() == null
                ? conversation + ":" + turn.getSequenceNum()
                : String.valueOf(turn.getId());
        var pendingOperation = harnessOperationStore.findPending(tenantId, userId, conversation);
        String operationId = pendingOperation
                .map(com.microservice.framework.ai.harness.runtime.model.HarnessOperation::operationId)
                .orElseGet(() -> UUID.randomUUID().toString());
        Set<String> permissions = new LinkedHashSet<>();
        if (context.funcPermissionList() != null) {
            permissions.addAll(context.funcPermissionList());
        }
        return new HarnessInvocation(tenantId, userId, conversation, operationId, turnId, permissions,
                pendingOperation.isPresent());
    }

    private ToolsetSelectionContext createToolsetSelectionContext(HarnessInvocation invocation, String route,
                                                                  String taskType, Set<String> requestedToolsets) {
        String[] profiles = environment.getActiveProfiles();
        String activeEnvironment = profiles.length == 0 ? "default" : profiles[0];
        return new ToolsetSelectionContext(invocation.tenantId(), invocation.userId(), invocation.conversationId(),
                invocation.turnId(), route, taskType, activeEnvironment, invocation.permissions(), requestedToolsets,
                Set.of());
    }

    private InvocationParameters invocationParameters(HarnessInvocation invocation,
                                                      ToolsetSelectionContext selectionContext) {
        return InvocationParameters.from(java.util.Map.of(
                HarnessInvocation.PARAMETER_KEY, invocation,
                ToolsetSelectionContext.PARAMETER_KEY, selectionContext,
                DelegatedAuthorization.PARAMETER_KEY, delegatedAuthorizationProvider.current()));
    }

    private ChatAgent platformChatAgent(AskReq askReq) {
        if (askReq.getChatType() == ConversationType.PLATFORM_AGENT) {
            if (StrUtil.isBlank(askReq.getModelId())) {
                throw CheckedException.badRequest("平台助手缺少可用的对话模型");
            }
            return ChatAgent.builder()
                    .id(0L)
                    .name("MicroService AI")
                    .description("MicroService 平台智能助手")
                    .modelId(Long.valueOf(askReq.getModelId()))
                    .toolsetIds(List.of())
                    .build();
        }
        ChatAgent chatAgent = chatAgentService.getById(askReq.getAgentId());
        if (chatAgent == null) {
            throw CheckedException.notFound(
                    String.format(AiServiceConstants.ERROR_AGENT_NOT_FOUND, askReq.getAgentId()));
        }
        return chatAgent;
    }

    private List<ConversationTurn> excludeSummarizedTurns(ConversationSummary summary,
                                                          List<ConversationTurn> history) {
        if (summary == null || summary.getCoveredUntilSequenceNum() == null || history == null || history.isEmpty()) {
            return history == null ? List.of() : history;
        }
        int coveredUntil = summary.getCoveredUntilSequenceNum();
        return history.stream()
                .filter(turn -> turn.getSequenceNum() == null || turn.getSequenceNum() > coveredUntil)
                .toList();
    }

    /**
     * 在消息进入 LangChain4j 记忆前应用动态预算，完整历史仍保留在数据库中。
     * @param toolSchemaTokens toolSchemaTokens 参数
     * @param currentInput currentInput 参数
     * @param history 历史消息
     * @param maxMessages 最大消息数
     * @param systemPromptTokens systemPromptTokens 参数
     * @param model 模型实例
     * @param summary 会话摘要
     * @return 处理结果
     */
    private List<ConversationTurn> selectBudgetedHistory(ModelEntity model, ConversationSummary summary,
                                                         String currentInput, List<ConversationTurn> history, int maxMessages, int systemPromptTokens,
                                                         int toolSchemaTokens) {
        ConversationContextProperties.Budget configured = contextProperties.getBudget();
        if (!configured.isEnabled()) {
            return history == null ? List.of() : history;
        }
        int contextWindow = positiveModelVariable(model, CONTEXT_WINDOW_VARIABLE,
                configured.getContextWindowTokens());
        int reservedOutput = positiveModelVariable(model, MAX_OUTPUT_VARIABLE,
                configured.getReservedOutputTokens());
        String summaryContent = summary == null ? null : summary.getSummaryContent();
        ContextBudgetPlanner.ContextBudget budget = BUDGET_PLANNER.plan(new ContextBudgetPlanner.ContextCosts(
                contextWindow,
                reservedOutput,
                configured.getSafetyBufferTokens(),
                systemPromptTokens,
                toolSchemaTokens,
                TOKEN_ESTIMATOR.estimate(currentInput),
                TOKEN_ESTIMATOR.estimate(summaryContent)));
        return ConversationMemoryFactory.selectRecentTurns(history, maxMessages, budget.historyTokens());
    }

    private int positiveModelVariable(ModelEntity model, String key, int fallback) {
        if (model == null || model.getVariables() == null) {
            return fallback;
        }
        Object raw = model.getVariables().get(key);
        try {
            int value = raw instanceof Number number ? number.intValue() : Integer.parseInt(String.valueOf(raw));
            return value > 0 ? value : fallback;
        } catch (RuntimeException ignored) {
            log.warn("模型上下文变量格式无效，使用平台默认值: key={}, value={}", key, raw);
            return fallback;
        }
    }

    /**
     * 校验会话归属，防止横向越权。会话不存在或不属于当前用户时抛出异常。
     * @param conversationId 会话标识
     * @param userId 用户标识
     * @throws CheckedException 处理失败时抛出
     */
    private void assertConversationOwnership(Long conversationId, Long userId) {
        if (conversationId == null) {
            return;
        }
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new CheckedException(403, "无权访问该会话");
        }
    }

    private Conversation findOrCreateConversation(Long userId, AskReq askReq, ConversationType type) {
        Conversation conversation;
        if (type == ConversationType.KNOWLEDGE_BASE) {
            var query = Wraps.<Conversation>lbQ().eq(Conversation::getUserId, userId)
                    // 使用两端补逗号的精确匹配，兼容 MySQL 与 PostgreSQL 的逗号分隔历史数据。
                    .apply("CONCAT(',', knowledge_base_ids, ',') LIKE CONCAT('%,', {0}, ',%')", askReq.getKbId());
            if (askReq.getConversationId() != null) {
                conversation = conversationService.getOne(query.eq(Conversation::getId, askReq.getConversationId()));
                if (conversation == null) {
                    throw CheckedException.badRequest("会话不存在或不属于当前知识库");
                }
            } else {
                conversation = conversationService.getOne(query.orderByDesc(Conversation::getLastModifyTime)
                        .orderByDesc(Conversation::getId).last("LIMIT 1"));
            }
        } else {
            var query = Wraps.<Conversation>lbQ().eq(Conversation::getUserId, userId)
                    .eq(Conversation::getType, type)
                    .eq(Conversation::getAgentId, askReq.getAgentId());
            if (askReq.getConversationId() != null) {
                conversation = conversationService.getOne(query.eq(Conversation::getId, askReq.getConversationId()));
                if (conversation == null) {
                    throw CheckedException.badRequest("会话不存在或不属于当前智能体");
                }
            } else {
                conversation = conversationService.getOne(query.orderByDesc(Conversation::getLastModifyTime)
                        .orderByDesc(Conversation::getId).last("LIMIT 1"));
            }
        }
        if (conversation == null) {
            String title = StrUtil.subPre(StrUtil.blankToDefault(askReq.getPrompt(), "新对话"), 20);
            List<Long> knowledgeBaseIds = askReq.getKbId() == null ? List.of() : List.of(askReq.getKbId());
            conversation = Conversation.builder().title(title).type(type).userId(userId)
                    .tenantId(context.tenantId()).knowledgeBaseIds(knowledgeBaseIds).agentId(askReq.getAgentId())
                    .messageCount(0).pinned(false).lastModifyTime(Instant.now()).build();
            conversationService.save(conversation);
            log.info("创建新会话: conversationId={}, type={}, userId={}", conversation.getId(), type, userId);
        }
        return conversation;
    }

    /**
     * 为智能体构建RAG参数
     * @param chatAgent chatAgent 参数
     * @param textModelEntity textModelEntity 参数
     * @return 处理结果
     */
    private RagAssistantParams buildRagParamsForAgent(ChatAgent chatAgent, ModelEntity textModelEntity) {
        if (chatAgent.getKbId() == null) {
            return null;
        }

        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(chatAgent.getKbId());
        if (knowledgeBase == null) {
            log.warn("智能体关联的知识库不存在: agentId={}, kbId={}",
                    chatAgent.getId(), chatAgent.getKbId());
            return null;
        }

        ModelEntity embeddingModelEntity = modelConfigRetriever.getModelByIdAndType(
                knowledgeBase.getEmbedModelId(), ModelType.EMBEDDING);

        ModelEntity rerankModelEntity = modelConfigRetriever.getModel(knowledgeBase.getRerankModelId())
                .orElse(null);

        return RagAssistantParams.builder().kbId(chatAgent.getKbId())
                .textModelEntity(textModelEntity).embeddingModelEntity(embeddingModelEntity)
                .rerankModelEntity(rerankModelEntity).enableGraphRetrieval(knowledgeBase.getEnableGraph()).build();
    }

    /**
     * 保存助手消息
     * @param result 处理结果
     * @param modelEntity 模型配置
     * @param parentMessageId parentMessageId 参数
     * @param conversationId 会话标识
     * @param tenantId 租户标识
     * @param userId 用户标识
     */
    private void saveAssistantMessage(Long conversationId, Long userId, Long tenantId,
                                      ModelEntity modelEntity, Long parentMessageId,
                                      ChatCompletionResult result) {
        String rawContent = result.content();
        Integer promptTokens = result.inputTokens();
        Integer completionTokens = result.outputTokens();

        AssistantMessageSaveReq req = AssistantMessageSaveReq.builder()
                .conversationId(conversationId)
                .userId(userId)
                .tenantId(tenantId)
                .rawContent(rawContent)
                .displayContent(rawContent)
                .thinkingContent(result.thinking())
                .references(result.references())
                .modelName(modelEntity.getName())
                .modelProvider(modelEntity.getProvider().getLabel())
                .promptTokens(promptTokens)
                .completionTokens(completionTokens)
                .parentMessageId(parentMessageId)
                .build();
        conversationMessageService.saveAssistantMessage(req);
    }

    private void refreshSummary(Long conversationId, Integer beforeSequenceNum) {
        try {
            conversationSummaryService.refreshSummaryIfNeeded(conversationId, beforeSequenceNum);
        } catch (Exception e) {
            log.error("刷新会话摘要失败: conversationId={}", conversationId, e);
        }
    }

    /**
     * 处理对话错误
     * @param errorMessage 错误信息
     * @param sseEmitter sseEmitter 参数
     */
    private void handleChatError(SseEmitter sseEmitter, String errorMessage) {
        try {
            sseEmitter.send(SseEmitter.event().name("error").data(errorMessage));
            sseEmitter.complete();
        } catch (Exception ex) {
            log.error("发送错误信息失败", ex);
        }
    }
}
