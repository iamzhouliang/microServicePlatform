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
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.annotation.TableName;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.dto.resp.ConversationMessageResp;
import com.microservice.platform.ai.domain.entity.Conversation;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationMapper;
import com.microservice.platform.ai.repository.ConversationMessageMapper;
import com.microservice.platform.ai.repository.ConversationSummaryMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Conversation service")
class ConversationServiceImplTest {

    @Test
    @DisplayName("普通聊天历史只返回用户可见消息")
    void turnListExcludesLangChain4jToolLoopMessages() {
        AuthenticationContext context = mock(AuthenticationContext.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
        when(context.userId()).thenReturn(1L);
        when(conversationMapper.selectById(10L)).thenReturn(Conversation.builder().id(10L).userId(1L).build());
        when(messageMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                ConversationTurn.builder().role(MessageRole.USER).displayContent("请查询用户").build(),
                ConversationTurn.builder().role(MessageRole.ASSISTANT)
                        .variables(Map.of(ConversationMessageServiceImpl.MESSAGE_KIND,
                                ConversationMessageServiceImpl.AI_TOOL_REQUEST))
                        .build(),
                ConversationTurn.builder().role(MessageRole.TOOL).displayContent("内部工具结果").build(),
                ConversationTurn.builder().role(MessageRole.ASSISTANT).displayContent("查询完成").build()));
        ConversationServiceImpl service = new ConversationServiceImpl(context, messageMapper,
                mock(ConversationSummaryMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", conversationMapper);

        List<ConversationMessageResp> messages = service.turnList(10L);

        assertThat(messages)
                .extracting(ConversationMessageResp::getRole, ConversationMessageResp::getDisplayContent)
                .containsExactly(tuple(MessageRole.USER, "请查询用户"), tuple(MessageRole.ASSISTANT, "查询完成"));
    }

    @Test
    @DisplayName("enables JSON result mapping for persisted message references")
    void enablesJsonResultMappingForPersistedMessageReferences() {
        assertThat(ConversationTurn.class.getAnnotation(TableName.class).autoResultMap()).isTrue();
    }

    @Test
    @DisplayName("loads knowledge base history for scalar and array JSON storage")
    void loadsKnowledgeBaseHistoryForScalarAndArrayJsonStorage() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Conversation.class);
        AuthenticationContext context = mock(AuthenticationContext.class);
        ConversationMapper conversationMapper = mock(ConversationMapper.class);
        ConversationMessageMapper messageMapper = mock(ConversationMessageMapper.class);
        when(context.userId()).thenReturn(1L);
        when(conversationMapper.selectOne(any(Wrapper.class))).thenReturn(null);
        ConversationServiceImpl service = new ConversationServiceImpl(context, messageMapper,
                mock(ConversationSummaryMapper.class));
        ReflectionTestUtils.setField(service, "baseMapper", conversationMapper);

        service.messageList(2075511127940464641L, null);

        ArgumentCaptor<Wrapper<Conversation>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(conversationMapper).selectOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment())
                .contains("CONCAT(")
                .doesNotContain("FIND_IN_SET")
                .doesNotContain("JSON_ARRAY");
    }
}
