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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static com.microservice.platform.ai.service.impl.ConversationMessageServiceImpl.*;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("对话历史记忆工厂")
class ConversationMemoryFactoryTest {

    @Test
    @DisplayName("将持久化用户和助手消息按原顺序转换为 LangChain4j 消息")
    void toChatMessagesConvertsPersistedTurnsInOrder() {
        List<ChatMessage> messages = ConversationMemoryFactory.toChatMessages(List.of(
                turn(MessageRole.USER, "我叫张三", null, null),
                turn(MessageRole.ASSISTANT, null, "好的，我会记住。", "好的，我会记住。"),
                turn(MessageRole.TOOL, "工具输出", "工具输出", "工具输出"),
                turn(MessageRole.USER, "   ", null, null)));

        assertThat(messages).containsExactly(
                UserMessage.from("我叫张三"),
                AiMessage.from("好的，我会记住。"));
    }

    @Test
    @DisplayName("创建记忆提供者时预置历史消息并保留窗口上限")
    void createProviderSeedsHistoryAndKeepsWindowLimit() {
        List<ChatMessage> history = List.of(
                UserMessage.from("第一条"),
                AiMessage.from("第一条回复"),
                UserMessage.from("第二条"),
                AiMessage.from("第二条回复"));

        ChatMemory memory = ConversationMemoryFactory.createProvider(history, 3).get(100L);

        assertThat(memory.messages()).containsExactly(
                AiMessage.from("第一条回复"),
                UserMessage.from("第二条"),
                AiMessage.from("第二条回复"));
    }

    @Test
    @DisplayName("创建记忆提供者时将会话摘要放在最近原文之前")
    void createProviderSeedsSummaryBeforeRecentHistory() {
        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("用户叫张三，偏好 PostgreSQL。")
                .build();
        List<ConversationTurn> recentTurns = List.of(
                turn(MessageRole.USER, "继续刚才的问题", null, null),
                turn(MessageRole.ASSISTANT, null, "可以继续。", "可以继续。"));

        ChatMemory memory = ConversationMemoryFactory.createProvider(summary, recentTurns, 2).get(100L);

        assertThat(memory.messages()).containsExactly(
                SystemMessage.from("""
                        【会话摘要】
                        用户叫张三，偏好 PostgreSQL。
                        """),
                UserMessage.from("继续刚才的问题"),
                AiMessage.from("可以继续。"));
    }

    @Test
    @DisplayName("完整恢复 AI Tool Request、Tool Result 及官方 Skills 激活属性")
    void restoresCompleteToolLoopMessages() {
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("activate-1")
                .name("activate_skill").arguments("{\"skill_name\":\"iam-user-management\"}").build();
        ConversationTurn aiToolRequest = ConversationTurn.builder().role(MessageRole.ASSISTANT)
                .thinkingContent("修复前遗留的私有推理")
                .variables(Map.of(MESSAGE_KIND, AI_TOOL_REQUEST, TOOL_REQUESTS, List.of(Map.of(
                        TOOL_CALL_ID, request.id(), TOOL_NAME, request.name(), "arguments", request.arguments())),
                        ATTRIBUTES, Map.of()))
                .build();
        ConversationTurn toolResult = ConversationTurn.builder().role(MessageRole.TOOL)
                .modelOutput("技能说明").variables(Map.of(MESSAGE_KIND, TOOL_RESULT,
                        TOOL_CALL_ID, request.id(), TOOL_NAME, request.name(), TOOL_ERROR, false,
                        ATTRIBUTES, Map.of("activated_skill", "iam-user-management")))
                .build();

        List<ChatMessage> restored = ConversationMemoryFactory.toChatMessages(List.of(aiToolRequest, toolResult));

        assertThat(restored).containsExactly(
                AiMessage.builder().toolExecutionRequests(List.of(request)).attributes(Map.of()).build(),
                ToolExecutionResultMessage.builder().id(request.id()).toolName(request.name()).text("技能说明")
                        .isError(false).attributes(Map.of("activated_skill", "iam-user-management")).build());
    }

    @Test
    @DisplayName("普通聊天历史只保留用户消息和最终可见回复")
    void publicHistoryExcludesToolLoopInternals() {
        ToolExecutionRequest request = ToolExecutionRequest.builder().id("activate-1")
                .name("activate_skill").arguments("{\"skill_name\":\"iam-user-management\"}").build();
        ConversationTurn toolRequest = ConversationTurn.builder().role(MessageRole.ASSISTANT)
                .modelOutput("好的，我先激活相关技能。").thinkingContent("私有推理")
                .variables(Map.of(MESSAGE_KIND, AI_TOOL_REQUEST, TOOL_REQUESTS, List.of(Map.of(
                        TOOL_CALL_ID, request.id(), TOOL_NAME, request.name(), "arguments", request.arguments())),
                        ATTRIBUTES, Map.of()))
                .build();
        ConversationTurn toolResult = ConversationTurn.builder().role(MessageRole.TOOL)
                .modelOutput("内部技能正文").variables(Map.of(MESSAGE_KIND, TOOL_RESULT,
                        TOOL_CALL_ID, request.id(), TOOL_NAME, request.name(), TOOL_ERROR, false,
                        ATTRIBUTES, Map.of()))
                .build();
        List<ConversationTurn> turns = List.of(
                turn(MessageRole.USER, "查询 admin", null, null),
                toolRequest,
                toolResult,
                turn(MessageRole.ASSISTANT, null, "模型原始最终回复", "查询完成"),
                turn(MessageRole.USER, "写一段秋景", null, null));

        ChatMemory memory = ConversationMemoryFactory.createPublicProviderFromTurns(turns, 10).get(100L);

        assertThat(memory.messages()).containsExactly(
                UserMessage.from("查询 admin"),
                AiMessage.from("查询完成"),
                UserMessage.from("写一段秋景"));
        assertThat(memory.messages().toString())
                .doesNotContain("activate_skill", "激活相关技能", "内部技能正文", "私有推理", "模型原始最终回复");
    }

    @Test
    @DisplayName("按 Token 和消息双重上限选择最近原文且不改变顺序")
    void selectRecentTurnsUsesDynamicTokenBudget() {
        List<ConversationTurn> turns = List.of(
                turn(MessageRole.USER, "较早问题".repeat(20), null, null),
                turn(MessageRole.ASSISTANT, null, "较早回答".repeat(20), "较早回答".repeat(20)),
                turn(MessageRole.USER, "最近问题", null, null),
                turn(MessageRole.ASSISTANT, null, "最近回答", "最近回答"));

        List<ConversationTurn> selected = ConversationMemoryFactory.selectRecentTurns(turns, 4, 8);

        assertThat(selected).containsExactly(turns.get(2), turns.get(3));
    }

    private static ConversationTurn turn(MessageRole role, String userInput, String modelOutput, String displayContent) {
        return ConversationTurn.builder()
                .role(role)
                .userInput(userInput)
                .modelOutput(modelOutput)
                .displayContent(displayContent)
                .build();
    }
}
