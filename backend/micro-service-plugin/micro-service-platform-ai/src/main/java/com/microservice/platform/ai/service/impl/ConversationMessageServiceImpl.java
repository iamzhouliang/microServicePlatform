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

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.dto.req.AssistantMessageSaveReq;
import com.microservice.platform.ai.domain.dto.req.UserMessageSaveReq;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationMessageMapper;
import com.microservice.platform.ai.service.ConversationMessageService;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 会话消息服务实现类
 *
 * @author xiao1
 * @since 2025-10
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationMessageServiceImpl extends SuperServiceImpl<ConversationMessageMapper, ConversationTurn> implements ConversationMessageService {

    public static final String MESSAGE_KIND = "lc4j_message_kind";
    public static final String AI_TOOL_REQUEST = "ai_tool_request";
    public static final String TOOL_RESULT = "tool_result";
    public static final String TOOL_REQUESTS = "tool_execution_requests";
    public static final String ATTRIBUTES = "attributes";
    public static final String TOOL_CALL_ID = "tool_call_id";
    public static final String TOOL_NAME = "tool_name";
    public static final String TOOL_ERROR = "tool_error";

    private final ConversationMessageMapper messageMapper;

    @Transactional(propagation = Propagation.NESTED)
    @Override
    public ConversationTurn saveUserMessage(UserMessageSaveReq req) {
        ConversationTurn message = null;
        try {
            Integer sequenceNum = getNextSequence(req.getConversationId());
            Instant now = Instant.now();
            message = ConversationTurn.builder()
                    .conversationId(req.getConversationId())
                    .userId(req.getUserId())
                    .tenantId(req.getTenantId())
                    .role(MessageRole.USER)
                    .userInput(req.getRawContent())
                    .displayContent(req.getRawContent())
                    .modelOutput(req.getRawContent())
                    .inputTokens(req.getPromptTokens())
                    .outputTokens(0)
                    .sequenceNum(sequenceNum)
                    .createTime(now)
                    .lastModifyTime(now)
                    .deleted(false)
                    .build();
            messageMapper.insert(message);
            log.debug("用户消息已保存，conversationId={}, messageId={}", req.getConversationId(), message.getId());
        } catch (Exception e) {
            log.error("保存用户消息失败", e);
        }
        return message;
    }

    @Override
    public List<ConversationTurn> listContextTurns(Long conversationId, Integer beforeSequenceNum, int recentRounds) {
        if (recentRounds <= 0) {
            return List.of();
        }
        return listPriorTurns(conversationId, beforeSequenceNum, recentRounds * 2);
    }

    @Override
    public List<ConversationTurn> listTurnsBetween(Long conversationId, Integer afterSequenceNum,
                                                   Integer beforeSequenceNum, int maxMessages) {
        if (conversationId == null || beforeSequenceNum == null || maxMessages <= 0) {
            return List.of();
        }
        Page<ConversationTurn> page = page(new Page<>(1, maxMessages, false), Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, conversationId)
                .gt(ConversationTurn::getSequenceNum, afterSequenceNum)
                .lt(ConversationTurn::getSequenceNum, beforeSequenceNum)
                .in(ConversationTurn::getRole, List.of(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL))
                .orderByAsc(ConversationTurn::getSequenceNum));
        return page.getRecords();
    }

    private List<ConversationTurn> listPriorTurns(Long conversationId, Integer beforeSequenceNum, int maxMessages) {
        if (conversationId == null || beforeSequenceNum == null) {
            return List.of();
        }
        Page<ConversationTurn> page = page(new Page<>(1, maxMessages, false), Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, conversationId)
                .lt(ConversationTurn::getSequenceNum, beforeSequenceNum)
                .in(ConversationTurn::getRole, List.of(MessageRole.USER, MessageRole.ASSISTANT, MessageRole.TOOL))
                .orderByDesc(ConversationTurn::getSequenceNum));
        List<ConversationTurn> records = new ArrayList<>(page.getRecords());
        records.sort(Comparator.comparing(ConversationTurn::getSequenceNum));
        return records;
    }

    @Transactional(propagation = Propagation.NESTED)
    @Override
    public void saveAssistantMessage(AssistantMessageSaveReq req) {
        if (req.getParentMessageId() == null) {
            log.warn("会话 {} 中未找到用户消息，AI回复无法建立 parent 关系", req.getConversationId());
        }

        Integer sequenceNum = getNextSequence(req.getConversationId());
        Integer promptTokens = req.getPromptTokens();
        Integer completionTokens = req.getCompletionTokens();
        Instant now = Instant.now();

        ConversationTurn message = ConversationTurn.builder()
                .conversationId(req.getConversationId())
                .userId(req.getUserId())
                .tenantId(req.getTenantId())
                .role(MessageRole.ASSISTANT)
                .modelOutput(req.getRawContent())
                .displayContent(req.getDisplayContent())
                .finalPrompt(req.getPromptContent())
                .modelName(req.getModelName())
                .modelProvider(AiProvider.of(req.getModelProvider()))
                .inputTokens(promptTokens)
                .outputTokens(completionTokens)
                .inferenceLatencyMs(req.getResponseLatencyMs())
                .thinkingContent(req.getThinkingContent())
                .variables(referenceVariables(req.getReferences()))
                .sequenceNum(sequenceNum)
                .previousTurnId(req.getParentMessageId())
                .createTime(now)
                .lastModifyTime(now)
                .deleted(false)
                .build();

        messageMapper.insert(message);
        log.info("AI 回复消息已保存，conversationId={}, messageId={}, inputTokens={}, outputTokens={}",
                req.getConversationId(), message.getId(), promptTokens, completionTokens);
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void saveToolLoopMessage(Long conversationId, Long userId, Long tenantId, ChatMessage message) {
        ConversationTurn turn = toToolLoopTurn(conversationId, userId, tenantId, message);
        if (turn == null) {
            return;
        }
        turn.setSequenceNum(getNextSequence(conversationId));
        Instant now = Instant.now();
        turn.setCreateTime(now);
        turn.setLastModifyTime(now);
        turn.setDeleted(false);
        messageMapper.insert(turn);
    }

    private static ConversationTurn toToolLoopTurn(Long conversationId, Long userId, Long tenantId,
                                                   ChatMessage message) {
        if (message instanceof AiMessage aiMessage && aiMessage.hasToolExecutionRequests()) {
            Map<String, Object> variables = new java.util.LinkedHashMap<>();
            variables.put(MESSAGE_KIND, AI_TOOL_REQUEST);
            variables.put(TOOL_REQUESTS, aiMessage.toolExecutionRequests().stream()
                    .map(ConversationMessageServiceImpl::toolRequestMap).toList());
            variables.put(ATTRIBUTES, aiMessage.attributes());
            // Tool Loop 私有推理不进入持久化存储；恢复只依赖标准 Tool 请求与属性。
            return ConversationTurn.builder().conversationId(conversationId).userId(userId).tenantId(tenantId)
                    .role(MessageRole.ASSISTANT).modelOutput(aiMessage.text())
                    .variables(Map.copyOf(variables)).build();
        }
        if (message instanceof ToolExecutionResultMessage result) {
            Map<String, Object> variables = new java.util.LinkedHashMap<>();
            variables.put(MESSAGE_KIND, TOOL_RESULT);
            variables.put(TOOL_CALL_ID, result.id());
            variables.put(TOOL_NAME, result.toolName());
            variables.put(TOOL_ERROR, Boolean.TRUE.equals(result.isError()));
            variables.put(ATTRIBUTES, result.attributes());
            return ConversationTurn.builder().conversationId(conversationId).userId(userId).tenantId(tenantId)
                    .role(MessageRole.TOOL).modelOutput(result.text()).displayContent(result.text())
                    .variables(Map.copyOf(variables)).build();
        }
        return null;
    }

    private static Map<String, Object> toolRequestMap(ToolExecutionRequest request) {
        return Map.of(TOOL_CALL_ID, request.id(), TOOL_NAME, request.name(), "arguments", request.arguments());
    }

    private Integer getNextSequence(Long conversationId) {
        Integer maxSeq = messageMapper.selectMaxSequenceByConversationId(conversationId);
        return maxSeq == null ? 1 : maxSeq + 1;
    }

    private static Map<String, Object> referenceVariables(List<ChatReference> references) {
        if (references == null || references.isEmpty()) {
            return null;
        }
        return Map.of(ChatReference.VARIABLES_KEY, references.stream().map(ChatReference::toMap).toList());
    }
}
