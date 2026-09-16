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

import com.microservice.framework.ai.harness.memory.ApproximateTokenEstimator;
import com.microservice.framework.ai.harness.memory.ContextBudgetPlanner;
import com.microservice.framework.ai.harness.memory.OrphanToolCallRepair;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.Map;
import java.util.ArrayList;
import java.util.function.Consumer;

import static com.microservice.platform.ai.service.impl.ConversationMessageServiceImpl.*;

import static com.microservice.platform.ai.core.constant.AiServiceConstants.DEFAULT_MAX_MESSAGES;

/**
 * 会话历史记忆工厂.
 *
 * @return 处理结果
 * @author xiao1
 * @since 2026-06
 */
public final class ConversationMemoryFactory {

    private static final ApproximateTokenEstimator TOKEN_ESTIMATOR = new ApproximateTokenEstimator();
    private static final ContextBudgetPlanner BUDGET_PLANNER = new ContextBudgetPlanner();
    private static final OrphanToolCallRepair TOOL_CALL_REPAIR = new OrphanToolCallRepair();

    private ConversationMemoryFactory() {
    }

    public static List<ChatMessage> toChatMessages(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(ConversationMemoryFactory::toChatMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 将持久化历史投影为普通聊天可见上下文。
     * <p>Tool 请求、Tool 结果和私有思考属于业务运行时内部状态，不能进入无 Tool 的 CHAT 模型。</p>
     * @param turns turns 参数
     * @return 处理结果
     */
    public static List<ChatMessage> toPublicChatMessages(List<ConversationTurn> turns) {
        if (turns == null || turns.isEmpty()) {
            return List.of();
        }
        return turns.stream()
                .map(ConversationMemoryFactory::toPublicChatMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    public static ChatMemoryProvider createProvider(List<ChatMessage> history, int maxMessages) {
        return createProvider(history, maxMessages, null);
    }

    /**
     * 创建可观察 Tool Loop 中间消息的记忆提供者。
     * @param appendObserver appendObserver 参数
     * @param history 历史消息
     * @param maxMessages 最大消息数
     * @return 处理结果
     */
    public static ChatMemoryProvider createProvider(List<ChatMessage> history, int maxMessages,
                                                    Consumer<ChatMessage> appendObserver) {
        int windowSize = maxMessages > 0 ? maxMessages : DEFAULT_MAX_MESSAGES;
        List<ChatMessage> messages = TOOL_CALL_REPAIR.repair(history == null ? List.of() : history);
        return memoryId -> {
            ChatMemory memory = MessageWindowChatMemory.builder()
                    .id(memoryId)
                    .maxMessages(windowSize)
                    .build();
            messages.forEach(memory::add);
            return appendObserver == null ? memory : new ObservedChatMemory(memory, appendObserver);
        };
    }

    public static ChatMemoryProvider createProvider(ConversationSummary summary, List<ConversationTurn> turns,
                                                    int maxMessages) {
        List<ChatMessage> summaryMessages = toSummaryMessage(summary);
        List<ChatMessage> historyMessages = toChatMessages(turns);
        int historyWindowSize = maxMessages > 0 ? maxMessages : DEFAULT_MAX_MESSAGES;
        int windowSize = historyWindowSize + summaryMessages.size();
        return createProvider(Stream.concat(summaryMessages.stream(), historyMessages.stream()).toList(), windowSize);
    }

    public static ChatMemoryProvider createProviderFromTurns(List<ConversationTurn> turns, int maxMessages) {
        return createProvider(toChatMessages(turns), maxMessages);
    }

    public static ChatMemoryProvider createProviderFromTurns(List<ConversationTurn> turns, int maxMessages,
                                                             Consumer<ChatMessage> appendObserver) {
        return createProvider(toChatMessages(turns), maxMessages, appendObserver);
    }

    public static ChatMemoryProvider createPublicProviderFromTurns(List<ConversationTurn> turns, int maxMessages) {
        return createProvider(toPublicChatMessages(turns), maxMessages);
    }

    /**
     * 按消息数量和动态 Token 预算选择最近原文。
     * <p>选择发生在读取阶段，数据库中的完整会话不会被删除或改写。</p>
     *
     * @param turns       已按时间正序排列的候选消息
     * @param maxMessages 最大消息数量
     * @param maxTokens   最大历史 Token 预算
     * @return 保持原顺序的最近消息
     */
    public static List<ConversationTurn> selectRecentTurns(List<ConversationTurn> turns, int maxMessages,
                                                           int maxTokens) {
        return BUDGET_PLANNER.selectRecent(turns, ConversationMemoryFactory::estimateTokens,
                maxMessages, maxTokens);
    }

    private static List<ChatMessage> toSummaryMessage(ConversationSummary summary) {
        if (summary == null || StringUtils.isBlank(summary.getSummaryContent())) {
            return List.of();
        }
        return List.of(SystemMessage.from("""
                【会话摘要】
                %s
                """.formatted(summary.getSummaryContent().trim())));
    }

    static String withSummary(ConversationSummary summary, String systemPrompt) {
        if (summary == null || StringUtils.isBlank(summary.getSummaryContent())) {
            return systemPrompt;
        }
        return """
                【会话摘要】
                %s

                %s
                """.formatted(summary.getSummaryContent().trim(), StringUtils.defaultString(systemPrompt)).trim();
    }

    /**
     * 返回 Prompt 会话上下文层使用的摘要，不改变稳定 Prompt 前缀。
     * @param summary 会话摘要
     * @return 处理结果
     */
    static String summaryContext(ConversationSummary summary) {
        if (summary == null || StringUtils.isBlank(summary.getSummaryContent())) {
            return "";
        }
        return "【会话摘要】\n" + summary.getSummaryContent().trim();
    }

    private static ChatMessage toChatMessage(ConversationTurn turn) {
        if (turn == null || turn.getRole() == null) {
            return null;
        }
        if (turn.getRole() == MessageRole.USER) {
            String content = firstNotBlank(turn.getUserInput(), turn.getDisplayContent(), turn.getModelOutput());
            return StringUtils.isBlank(content) ? null : UserMessage.from(content);
        }
        if (turn.getRole() == MessageRole.ASSISTANT) {
            if (AI_TOOL_REQUEST.equals(messageKind(turn))) {
                return toolRequestMessage(turn);
            }
            String content = firstNotBlank(turn.getModelOutput(), turn.getDisplayContent());
            return StringUtils.isBlank(content) ? null : AiMessage.from(content);
        }
        if (turn.getRole() == MessageRole.TOOL && TOOL_RESULT.equals(messageKind(turn))) {
            return toolResultMessage(turn);
        }
        return null;
    }

    private static ChatMessage toPublicChatMessage(ConversationTurn turn) {
        if (turn == null || turn.getRole() == null) {
            return null;
        }
        if (turn.getRole() == MessageRole.USER) {
            String content = firstNotBlank(turn.getUserInput(), turn.getDisplayContent(), turn.getModelOutput());
            return StringUtils.isBlank(content) ? null : UserMessage.from(content);
        }
        if (turn.getRole() != MessageRole.ASSISTANT || AI_TOOL_REQUEST.equals(messageKind(turn))) {
            return null;
        }
        String content = firstNotBlank(turn.getDisplayContent(), turn.getModelOutput());
        return StringUtils.isBlank(content) ? null : AiMessage.from(content);
    }

    private static String messageKind(ConversationTurn turn) {
        return turn.getVariables() == null ? null : String.valueOf(turn.getVariables().get(MESSAGE_KIND));
    }

    private static ChatMessage toolRequestMessage(ConversationTurn turn) {
        Object raw = turn.getVariables().get(TOOL_REQUESTS);
        if (!(raw instanceof List<?> values)) {
            return null;
        }
        List<ToolExecutionRequest> requests = new ArrayList<>();
        for (Object value : values) {
            if (!(value instanceof Map<?, ?> map)) {
                continue;
            }
            requests.add(ToolExecutionRequest.builder().id(text(map.get(TOOL_CALL_ID)))
                    .name(text(map.get(TOOL_NAME))).arguments(text(map.get("arguments"))).build());
        }
        if (requests.isEmpty()) {
            return null;
        }
        // 兼容修复前数据：Tool Loop 恢复只需要标准 Tool 请求和属性，历史私有思考一律忽略。
        return AiMessage.builder().text(turn.getModelOutput())
                .toolExecutionRequests(requests).attributes(attributes(turn)).build();
    }

    private static ChatMessage toolResultMessage(ConversationTurn turn) {
        Map<String, Object> variables = turn.getVariables();
        return ToolExecutionResultMessage.builder().id(text(variables.get(TOOL_CALL_ID)))
                .toolName(text(variables.get(TOOL_NAME))).text(turn.getModelOutput())
                .isError(Boolean.TRUE.equals(variables.get(TOOL_ERROR))).attributes(attributes(turn)).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> attributes(ConversationTurn turn) {
        Object value = turn.getVariables().get(ATTRIBUTES);
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int estimateTokens(ConversationTurn turn) {
        ChatMessage message = toChatMessage(turn);
        if (message instanceof UserMessage userMessage) {
            return TOKEN_ESTIMATOR.estimate(userMessage.singleText());
        }
        if (message instanceof AiMessage aiMessage) {
            return TOKEN_ESTIMATOR.estimate(aiMessage.text());
        }
        return 0;
    }

    private static String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private static final class ObservedChatMemory implements ChatMemory {

        private final ChatMemory delegate;
        private final Consumer<ChatMessage> observer;

        private ObservedChatMemory(ChatMemory delegate, Consumer<ChatMessage> observer) {
            this.delegate = delegate;
            this.observer = observer;
        }

        @Override
        public Object id() {
            return delegate.id();
        }

        @Override
        public void add(ChatMessage message) {
            delegate.add(message);
            if (message instanceof ToolExecutionResultMessage
                    || message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
                observer.accept(message);
            }
        }

        @Override
        public List<ChatMessage> messages() {
            return delegate.messages();
        }

        @Override
        public void clear() {
            delegate.clear();
        }
    }
}
