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
import com.microservice.platform.ai.core.config.ConversationContextProperties;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationSummaryMapper;
import com.microservice.platform.ai.service.ConversationMessageService;
import com.microservice.platform.ai.service.ConversationSummaryGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("会话摘要服务")
class ConversationSummaryServiceImplTest {

    @Test
    @DisplayName("新增消息达到阈值时滚动生成摘要并推进覆盖序号")
    void summarizeWhenNewTurnsReachThreshold() {
        final ConversationSummaryMapper mapper = mock(ConversationSummaryMapper.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ConversationSummaryGenerator generator = mock(ConversationSummaryGenerator.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.getSummary().setEnabled(true);
        properties.getSummary().setMinMessages(4);
        properties.getSummary().setIncrementalMessages(2);
        ConversationSummaryServiceImpl service = new ConversationSummaryServiceImpl(messageService, generator, properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(ConversationSummary.builder()
                .conversationId(100L)
                .summaryContent("旧摘要")
                .coveredUntilSequenceNum(2)
                .build());
        List<ConversationTurn> uncoveredTurns = List.of(
                turn(3, MessageRole.USER, "问题一"),
                turn(4, MessageRole.ASSISTANT, "回答一"),
                turn(5, MessageRole.USER, "问题二"),
                turn(6, MessageRole.ASSISTANT, "回答二"));
        when(messageService.listTurnsBetween(100L, 2, 7, 100)).thenReturn(uncoveredTurns);
        when(generator.summarize("旧摘要", uncoveredTurns)).thenReturn("新摘要");

        service.refreshSummaryIfNeeded(100L, 7);

        ArgumentCaptor<ConversationSummary> captor = ArgumentCaptor.forClass(ConversationSummary.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getSummaryContent()).isEqualTo("新摘要");
        assertThat(captor.getValue().getCoveredUntilSequenceNum()).isEqualTo(6);
    }

    @Test
    @DisplayName("新增消息未达到阈值时不生成摘要")
    void skipWhenNewTurnsDoNotReachThreshold() {
        ConversationSummaryMapper mapper = mock(ConversationSummaryMapper.class);
        ConversationMessageService messageService = mock(ConversationMessageService.class);
        ConversationSummaryGenerator generator = mock(ConversationSummaryGenerator.class);
        ConversationContextProperties properties = new ConversationContextProperties();
        properties.getSummary().setEnabled(true);
        properties.getSummary().setIncrementalMessages(4);
        ConversationSummaryServiceImpl service = new ConversationSummaryServiceImpl(messageService, generator, properties);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectOne(any(Wrapper.class), eq(true))).thenReturn(ConversationSummary.builder()
                .conversationId(100L)
                .summaryContent("旧摘要")
                .coveredUntilSequenceNum(4)
                .build());

        service.refreshSummaryIfNeeded(100L, 6);

        verify(generator, never()).summarize(any(), any());
        verify(mapper, never()).updateById(any(ConversationSummary.class));
    }

    private static ConversationTurn turn(Integer sequenceNum, MessageRole role, String content) {
        return ConversationTurn.builder()
                .sequenceNum(sequenceNum)
                .role(role)
                .userInput(content)
                .modelOutput(content)
                .displayContent(content)
                .build();
    }
}
