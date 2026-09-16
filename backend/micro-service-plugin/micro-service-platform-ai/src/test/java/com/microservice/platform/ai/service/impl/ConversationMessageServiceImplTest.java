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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.dto.req.AssistantMessageSaveReq;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationMessageMapper;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("会话消息服务")
class ConversationMessageServiceImplTest {

    @Test
    @DisplayName("加载上下文时只取当前消息前的用户和助手消息并恢复时间正序")
    void listContextTurnsReturnsPriorUserAndAssistantMessagesInAscendingOrder() {
        ConversationMessageMapper mapper = mock(ConversationMessageMapper.class);
        ConversationMessageServiceImpl service = new ConversationMessageServiceImpl(mapper);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectPage(any(Page.class), any(Wrapper.class))).thenAnswer(invocation -> {
            Page<ConversationTurn> page = invocation.getArgument(0);
            page.setRecords(List.of(
                    turn(4, MessageRole.ASSISTANT),
                    turn(3, MessageRole.USER),
                    turn(2, MessageRole.ASSISTANT)));
            return page;
        });

        List<ConversationTurn> turns = service.listContextTurns(10L, 5, 2);

        assertThat(turns).extracting(ConversationTurn::getSequenceNum).containsExactly(2, 3, 4);
        verify(mapper).selectPage(org.mockito.ArgumentMatchers.argThat(page -> page.getSize() == 4), any(Wrapper.class));
    }

    @Test
    @DisplayName("保存助手消息时将引用来源持久化到变量")
    @SuppressWarnings("unchecked")
    void saveAssistantMessagePersistsReferencesInVariables() {
        ConversationMessageMapper mapper = mock(ConversationMessageMapper.class);
        ConversationMessageServiceImpl service = new ConversationMessageServiceImpl(mapper);
        when(mapper.selectMaxSequenceByConversationId(10L)).thenReturn(2);

        service.saveAssistantMessage(AssistantMessageSaveReq.builder()
                .conversationId(10L)
                .userId(20L)
                .tenantId(30L)
                .rawContent("回答")
                .displayContent("回答")
                .promptContent("问题")
                .modelName("qwen-plus")
                .modelProvider("qwen")
                .promptTokens(11)
                .completionTokens(22)
                .thinkingContent("分析")
                .references(List.of(new ChatReference(
                        "web",
                        "联网资料",
                        "https://example.com/news",
                        "Example",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "摘要",
                        Map.of("index", 1))))
                .parentMessageId(9L)
                .build());

        ArgumentCaptor<ConversationTurn> captor = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(mapper).insert(captor.capture());

        ConversationTurn saved = captor.getValue();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getLastModifyTime()).isNotNull();
        assertThat(saved.getThinkingContent()).isEqualTo("分析");
        assertThat(saved.getPreviousTurnId()).isEqualTo(9L);
        assertThat(saved.getVariables()).containsKey(ChatReference.VARIABLES_KEY);
        List<Map<String, Object>> references =
                (List<Map<String, Object>>) saved.getVariables().get(ChatReference.VARIABLES_KEY);
        assertThat(references)
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference).containsEntry("type", "web");
                    assertThat(reference).containsEntry("title", "联网资料");
                    assertThat(reference).containsEntry("url", "https://example.com/news");
                    assertThat(reference).containsEntry("siteName", "Example");
                    assertThat(reference).containsEntry("snippet", "摘要");
                });
    }

    @Test
    @DisplayName("保存助手消息失败时向调用方传播异常")
    void saveAssistantMessagePropagatesPersistenceFailure() {
        ConversationMessageMapper mapper = mock(ConversationMessageMapper.class);
        ConversationMessageServiceImpl service = new ConversationMessageServiceImpl(mapper);
        when(mapper.selectMaxSequenceByConversationId(10L)).thenReturn(2);
        when(mapper.insert(any(ConversationTurn.class))).thenThrow(new IllegalStateException("database unavailable"));

        AssistantMessageSaveReq req = AssistantMessageSaveReq.builder()
                .conversationId(10L)
                .userId(20L)
                .tenantId(30L)
                .rawContent("回答")
                .displayContent("回答")
                .modelName("qwen-plus")
                .modelProvider("qwen")
                .build();

        assertThatThrownBy(() -> service.saveAssistantMessage(req))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
    }

    @Test
    @DisplayName("持久化 Tool Loop 消息时不保存模型私有思考")
    void saveToolLoopMessageDropsPrivateThinking() {
        ConversationMessageMapper mapper = mock(ConversationMessageMapper.class);
        ConversationMessageServiceImpl service = new ConversationMessageServiceImpl(mapper);
        when(mapper.selectMaxSequenceByConversationId(10L)).thenReturn(2);
        AiMessage toolRequestMessage = AiMessage.builder()
                .thinking("包含密码和内部 Operation 的私有推理")
                .toolExecutionRequests(List.of(ToolExecutionRequest.builder()
                        .id("call-1")
                        .name("iam_search_user")
                        .arguments("{\"username\":\"admin\"}")
                        .build()))
                .build();

        service.saveToolLoopMessage(10L, 20L, 30L, toolRequestMessage);

        ArgumentCaptor<ConversationTurn> captor = ArgumentCaptor.forClass(ConversationTurn.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getThinkingContent()).isNull();
        assertThat(captor.getValue().getVariables()).containsKey("tool_execution_requests");
    }

    private static ConversationTurn turn(Integer sequenceNum, MessageRole role) {
        return ConversationTurn.builder()
                .conversationId(10L)
                .sequenceNum(sequenceNum)
                .role(role)
                .build();
    }
}
