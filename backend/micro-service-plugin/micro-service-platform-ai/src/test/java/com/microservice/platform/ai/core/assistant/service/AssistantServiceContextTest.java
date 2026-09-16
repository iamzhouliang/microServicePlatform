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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.core.provider.embedding.EmbeddingModelRegistry;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.provider.scoring.ScoringModelService;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.ToolService;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.prompt.PromptLayers;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("助手会话上下文")
class AssistantServiceContextTest {

    @Test
    @DisplayName("平台普通聊天只加载最小人格提示且不暴露业务工具")
    void platformChatKeepsBusinessCapabilitiesOutOfModelContext() {
        TextModelService textModelService = mock(TextModelService.class);
        ModelEntity model = ModelEntity.builder().id(20L).build();
        ChatModel chatModel = mock(ChatModel.class);
        CapturingStreamingChatModel streamingModel = new CapturingStreamingChatModel();
        when(textModelService.model(model)).thenReturn(chatModel);
        when(textModelService.streamModel(model)).thenReturn(streamingModel);
        AssistantService service = assistantService(textModelService);
        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("用户喜欢简洁回答。")
                .build();

        ChatAssistant assistant = service.createPlatformChatAssistant(model, summary, List.of(), 4);
        assistant.chatStream(100L, "写一首关于夏天的诗")
                .onError(error -> {
                })
                .start();

        assertThat(streamingModel.request.get()).isNotNull();
        assertThat(streamingModel.request.get().toolSpecifications()).isEmpty();
        assertThat(streamingModel.request.get().messages())
                .filteredOn(SystemMessage.class::isInstance)
                .singleElement()
                .satisfies(message -> assertThat(((SystemMessage) message).text())
                        .contains("MicroService AI", "自然", "真诚", "用户喜欢简洁回答")
                        .doesNotContain("平台业务", "Skill", "Tool", "审批", "Harness"));
    }

    @Test
    @DisplayName("平台助手将模型臆造的工具名转换为中文错误结果并继续对话")
    void platformAssistantContinuesAfterHallucinatedToolName() {
        TextModelService textModelService = mock(TextModelService.class);
        ModelEntity model = ModelEntity.builder().id(20L).build();
        ChatModel chatModel = mock(ChatModel.class);
        HallucinatedToolStreamingChatModel streamingModel = new HallucinatedToolStreamingChatModel();
        when(textModelService.model(model)).thenReturn(chatModel);
        when(textModelService.streamModel(model)).thenReturn(streamingModel);
        AssistantService service = assistantService(textModelService);
        ChatAssistant assistant = service.createPlatformAssistant(model, null, List.of(), 4,
                new PlatformAssistantRuntime.Setup(List.of(),
                        new PromptLayers("平台助手", "用户管理", ""), "用户管理"),
                message -> {
                });
        AtomicReference<Throwable> error = new AtomicReference<>();
        AtomicReference<ChatResponse> response = new AtomicReference<>();

        assistant.chatStreamWithContext(100L, "查询用户", new InvocationParameters())
                .onCompleteResponse(response::set)
                .onError(error::set)
                .start();

        assertThat(error.get()).isNull();
        assertThat(streamingModel.invocations.get()).isEqualTo(2);
        assertThat(streamingModel.retryRequest.get().messages())
                .filteredOn(ToolExecutionResultMessage.class::isInstance)
                .singleElement()
                .satisfies(message -> {
                    ToolExecutionResultMessage result = (ToolExecutionResultMessage) message;
                    assertThat(result.text()).contains("工具不存在", "当前可用工具");
                });
        assertThat(response.get().aiMessage().text()).isEqualTo("已根据可用能力继续处理");
    }

    @Test
    @DisplayName("联网普通对话声明实时搜索能力并遵守用户来源约束")
    void webSearchRequestReceivesGroundingSystemMessage() {
        TextModelService textModelService = mock(TextModelService.class);
        ModelEntity model = ModelEntity.builder()
                .id(20L)
                .enableWebSearch(true)
                .build();
        ChatModel chatModel = mock(ChatModel.class);
        CapturingStreamingChatModel streamingModel = new CapturingStreamingChatModel();
        when(textModelService.model(model)).thenReturn(chatModel);
        when(textModelService.streamModel(model)).thenReturn(streamingModel);
        AssistantService service = assistantService(textModelService);

        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("用户此前确认项目代号是 AURORA-731。")
                .build();
        ChatAssistant assistant = service.createMemoryAssistant(model, summary, List.of(), 4);
        assistant.chatStream(100L, "只依据 DeepSeek 官方网站回答")
                .onError(error -> {
                })
                .start();

        assertThat(streamingModel.request.get()).isNotNull();
        assertThat(streamingModel.request.get().messages())
                .filteredOn(SystemMessage.class::isInstance)
                .singleElement()
                .satisfies(message -> assertThat(((SystemMessage) message).text())
                        .contains("已启用实时联网搜索", "不得声称无法访问互联网", "用户指定来源",
                                "site:目标官方域名", "禁止使用或标注不符合来源限制", "使用 [数字] 角标",
                                "用户此前确认项目代号是 AURORA-731"));
    }

    @Test
    @DisplayName("智能体请求使用一个同时包含角色指令和会话摘要的系统消息")
    void agentRequestKeepsSummaryAlongsideSystemPrompt() {
        TextModelService textModelService = mock(TextModelService.class);
        ModelEntity model = ModelEntity.builder().id(20L).build();
        ChatModel chatModel = mock(ChatModel.class);
        CapturingStreamingChatModel streamingModel = new CapturingStreamingChatModel();
        when(textModelService.model(model)).thenReturn(chatModel);
        when(textModelService.streamModel(model)).thenReturn(streamingModel);
        AssistantService service = assistantService(textModelService);
        ChatAgent agent = ChatAgent.builder()
                .id(40L)
                .systemPrompt("你是项目支持助手。")
                .build();
        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("项目临时称呼是星桥计划。")
                .build();

        ChatAssistant assistant = service.createAgentAssistant(agent, model, null, summary, List.of(), 4);
        assistant.chatStream(100L, "继续")
                .onError(error -> {
                })
                .start();

        assertThat(streamingModel.request.get()).isNotNull();
        assertThat(streamingModel.request.get().messages())
                .filteredOn(SystemMessage.class::isInstance)
                .singleElement()
                .satisfies(message -> assertThat(((SystemMessage) message).text())
                        .contains("你是项目支持助手。", "项目临时称呼是星桥计划。"));
    }

    private static AssistantService assistantService(TextModelService textModelService) {
        return new AssistantService(
                textModelService,
                mock(VectorStoreFactory.class),
                mock(KnowledgeBaseService.class),
                mock(EmbeddingModelRegistry.class),
                new ObjectMapper(),
                mock(ToolService.class),
                mock(ToolsetResolver.class),
                mock(HarnessOperationCoordinator.class),
                mock(ScoringModelService.class),
                new PlatformPromptAssembler());
    }

    private static final class CapturingStreamingChatModel implements StreamingChatModel {

        private final AtomicReference<ChatRequest> request = new AtomicReference<>();

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            request.set(chatRequest);
        }
    }

    private static final class HallucinatedToolStreamingChatModel implements StreamingChatModel {

        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicReference<ChatRequest> retryRequest = new AtomicReference<>();

        @Override
        public void doChat(ChatRequest chatRequest, StreamingChatResponseHandler handler) {
            if (invocations.incrementAndGet() == 1) {
                ToolExecutionRequest request = ToolExecutionRequest.builder()
                        .id("call-unknown")
                        .name("iam_lookup_user")
                        .arguments("{\"username\":\"zhangsan\"}")
                        .build();
                handler.onCompleteResponse(ChatResponse.builder()
                        .aiMessage(AiMessage.from(request))
                        .build());
                return;
            }
            retryRequest.set(chatRequest);
            handler.onCompleteResponse(ChatResponse.builder()
                    .aiMessage(AiMessage.from("已根据可用能力继续处理"))
                    .build());
        }
    }
}
