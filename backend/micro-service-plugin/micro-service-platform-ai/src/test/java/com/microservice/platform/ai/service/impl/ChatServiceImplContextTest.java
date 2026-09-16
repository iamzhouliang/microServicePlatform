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
import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.prompt.PromptLayers;
import com.microservice.framework.redis.plus.lock.RedisLockHelper;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.assistant.routing.PlatformConversationRoute;
import com.microservice.platform.ai.core.assistant.routing.PlatformConversationRouter;
import com.microservice.platform.ai.core.assistant.service.AssistantService;
import com.microservice.platform.ai.core.assistant.service.RagAssistantParams;
import com.microservice.platform.ai.core.assistant.service.PlatformAssistantRuntime;
import com.microservice.platform.ai.core.config.ConversationContextProperties;
import com.microservice.platform.ai.core.enums.ConversationType;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.sse.SseChatHelper;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.domain.dto.result.ChatCompletionResult;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.domain.entity.Conversation;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.ChatAgentService;
import com.microservice.platform.ai.service.ConversationMessageService;
import com.microservice.platform.ai.service.ConversationService;
import com.microservice.platform.ai.service.ConversationSummaryService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.invocation.InvocationParameters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("聊天服务上下文加载")
class ChatServiceImplContextTest {

    @Test
    @DisplayName("平台普通请求只创建无业务工具的聊天助手")
    void platformChatRouteDoesNotCreateBusinessAssistant() {
        TestContext ctx = testContext();
        final AskReq req = askReq(ConversationType.PLATFORM_AGENT);
        ConversationTurn currentTurn = turn(7, MessageRole.USER, "写一首关于夏天的诗");
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);
        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(null);
        when(ctx.conversationMessageService.listContextTurns(100L, 7, 10)).thenReturn(List.of());
        when(ctx.modelConfigRetriever.getRequiredModel(20L, ModelType.TEXT)).thenReturn(model);
        when(ctx.platformAssistantRuntime.routingCapabilityIndex(any(ToolsetSelectionContext.class)))
                .thenReturn("用户管理：查询和创建用户");
        when(ctx.platformConversationRouter.route(eq(model), eq("当前问题"), eq(null), eq(List.of()),
                eq("用户管理：查询和创建用户"), eq(false))).thenReturn(PlatformConversationRoute.CHAT);
        when(ctx.assistantService.createPlatformChatAssistant(model, null, List.of(), 20)).thenReturn(assistant);
        when(assistant.chatStream(100L, "当前问题")).thenReturn(tokenStream);

        ctx.service.chatStream(req);

        verify(ctx.assistantService).createPlatformChatAssistant(model, null, List.of(), 20);
        verify(ctx.assistantService, never()).createPlatformAssistant(any(), any(), any(), any(Integer.class),
                any(), any());
        verify(ctx.platformAssistantRuntime, never()).create(any(HarnessInvocation.class));
        verify(assistant).chatStream(100L, "当前问题");
        verify(ctx.sseChatHelper).chatStreamToSse(eq(req), any(SseEmitter.class), eq(tokenStream),
                eq("token-100"), any());
    }

    @Test
    @DisplayName("普通对话在送入 LangChain4j 前按动态 Token 预算裁剪较早原文")
    void normalChatTrimsHistoryByDynamicTokenBudget() {
        TestContext ctx = testContext();
        ctx.contextProperties.getBudget().setContextWindowTokens(80);
        ctx.contextProperties.getBudget().setReservedOutputTokens(10);
        ctx.contextProperties.getBudget().setSafetyBufferTokens(10);
        ctx.contextProperties.getBudget().setNormalSystemPromptTokens(20);
        final AskReq req = askReq(ConversationType.NORMAL_TEXT);
        ConversationTurn currentTurn = turn(8, MessageRole.USER, "当前问题");
        ConversationTurn oversized = turn(3, MessageRole.USER, "较早大段内容".repeat(40));
        ConversationTurn recentUser = turn(6, MessageRole.USER, "最近问题");
        ConversationTurn recentAssistant = turn(7, MessageRole.ASSISTANT, "最近回答");
        List<ConversationTurn> loaded = List.of(oversized, recentUser, recentAssistant);
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);

        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(null);
        when(ctx.conversationMessageService.listContextTurns(100L, 8, 10)).thenReturn(loaded);
        when(ctx.modelConfigRetriever.getRequiredModel(20L)).thenReturn(model);
        when(ctx.assistantService.createMemoryAssistant(model, null,
                List.of(recentUser, recentAssistant), 20)).thenReturn(assistant);
        when(assistant.chatStream(100L, "当前问题")).thenReturn(tokenStream);

        ctx.service.chatStream(req);

        verify(ctx.assistantService).createMemoryAssistant(model, null,
                List.of(recentUser, recentAssistant), 20);
    }

    @Test
    @DisplayName("普通对话保存当前消息后只将历史消息传给记忆助手")
    void normalChatPassesPriorContextTurnsToMemoryAssistant() {
        TestContext ctx = testContext();
        final AskReq req = askReq(ConversationType.NORMAL_TEXT);
        ConversationTurn currentTurn = turn(5, MessageRole.USER, "当前问题");
        List<ConversationTurn> history = List.of(
                turn(3, MessageRole.USER, "我叫张三"),
                turn(4, MessageRole.ASSISTANT, "好的，我记住了"));
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);

        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        ConversationSummary summary = ConversationSummary.builder().summaryContent("旧摘要").build();
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(summary);
        when(ctx.conversationMessageService.listContextTurns(100L, 5, 10)).thenReturn(history);
        when(ctx.modelConfigRetriever.getRequiredModel(20L)).thenReturn(model);
        when(ctx.assistantService.createMemoryAssistant(model, summary, history, 20)).thenReturn(assistant);
        when(assistant.chatStream(100L, "当前问题")).thenReturn(tokenStream);

        ctx.service.chatStream(req);

        verify(ctx.conversationMessageService).listContextTurns(100L, 5, 10);
        verify(ctx.assistantService).createMemoryAssistant(model, summary, history, 20);
        ArgumentCaptor<Consumer<ChatCompletionResult>> callbackCaptor = ArgumentCaptor.forClass(Consumer.class);
        verify(ctx.sseChatHelper).chatStreamToSse(eq(req), any(SseEmitter.class), eq(tokenStream),
                eq("token-100"), callbackCaptor.capture());
        callbackCaptor.getValue().accept(new ChatCompletionResult("回答", 1, 2));
        verify(ctx.conversationSummaryService).refreshSummaryIfNeeded(100L, 5);
    }

    @Test
    @DisplayName("最近原文上下文排除已被摘要覆盖的消息")
    void contextHistoryExcludesTurnsCoveredBySummary() {
        TestContext ctx = testContext();
        final AskReq req = askReq(ConversationType.NORMAL_TEXT);
        ConversationTurn currentTurn = turn(7, MessageRole.USER, "当前问题");
        ConversationTurn summarizedTurn = turn(4, MessageRole.ASSISTANT, "已进入摘要");
        ConversationTurn recentTurn = turn(6, MessageRole.ASSISTANT, "摘要后的事实");
        List<ConversationTurn> loadedHistory = List.of(summarizedTurn, recentTurn);
        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("旧摘要")
                .coveredUntilSequenceNum(4)
                .build();
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);

        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(summary);
        when(ctx.conversationMessageService.listContextTurns(100L, 7, 10)).thenReturn(loadedHistory);
        when(ctx.modelConfigRetriever.getRequiredModel(20L)).thenReturn(model);
        when(ctx.assistantService.createMemoryAssistant(model, summary, List.of(recentTurn), 20))
                .thenReturn(assistant);
        when(assistant.chatStream(100L, "当前问题")).thenReturn(tokenStream);

        ctx.service.chatStream(req);

        verify(ctx.assistantService).createMemoryAssistant(model, summary, List.of(recentTurn), 20);
    }

    @Test
    @DisplayName("知识库对话保存当前消息后只将历史消息传给 RAG 助手")
    void ragChatPassesPriorContextTurnsToRagAssistant() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Conversation.class);
        final TestContext ctx = testContext();
        AskReq req = askReq(ConversationType.KNOWLEDGE_BASE);
        req.setConversationId(null);
        req.setKbId(30L);
        req.setMetadataFilter(Map.of("itemId", "88"));
        ConversationTurn currentTurn = turn(6, MessageRole.USER, "他叫什么");
        List<ConversationTurn> history = List.of(
                turn(3, MessageRole.USER, "我叫张三"),
                turn(4, MessageRole.ASSISTANT, "好的，我记住了"));
        KnowledgeBase knowledgeBase = KnowledgeBase.builder()
                .id(30L)
                .chatModelId(21L)
                .embedModelId(22L)
                .topK(7)
                .scoreThreshold(0.55)
                .enableGraph(false)
                .build();
        ModelEntity textModel = textModel(21L);
        ModelEntity embeddingModel = ModelEntity.builder()
                .id(22L)
                .type(ModelType.EMBEDDING)
                .provider(AiProvider.QWEN)
                .name("text-embedding-v2")
                .build();
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);

        when(ctx.knowledgeBaseService.getById(30L)).thenReturn(knowledgeBase);
        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        ConversationSummary summary = ConversationSummary.builder().summaryContent("RAG 旧摘要").build();
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(summary);
        when(ctx.conversationMessageService.listContextTurns(100L, 6, 8)).thenReturn(history);
        when(ctx.modelConfigRetriever.getModelByIdAndType(21L, ModelType.TEXT)).thenReturn(textModel);
        when(ctx.modelConfigRetriever.getModelByIdAndType(22L, ModelType.EMBEDDING)).thenReturn(embeddingModel);
        when(ctx.modelConfigRetriever.getModel(null)).thenReturn(Optional.empty());
        when(ctx.assistantService.createMemoryRagAssistant(any(RagAssistantParams.class), eq(summary), eq(history), eq(16))).thenReturn(assistant);
        when(assistant.chatStream(100L, "当前问题")).thenReturn(tokenStream);

        ctx.service.chatStream(req);

        ArgumentCaptor<Wrapper<Conversation>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(ctx.conversationService).getOne(queryCaptor.capture());
        assertThat(queryCaptor.getValue().getCustomSqlSegment())
                .contains("CONCAT(")
                .doesNotContain("FIND_IN_SET")
                .doesNotContain("JSON_ARRAY");
        ArgumentCaptor<RagAssistantParams> paramsCaptor = ArgumentCaptor.forClass(RagAssistantParams.class);
        verify(ctx.conversationMessageService).listContextTurns(100L, 6, 8);
        verify(ctx.assistantService).createMemoryRagAssistant(paramsCaptor.capture(), eq(summary), eq(history), eq(16));
        assertThat(paramsCaptor.getValue().getKbId()).isEqualTo(30L);
        assertThat(paramsCaptor.getValue().getMaxResults()).isEqualTo(7);
        assertThat(paramsCaptor.getValue().getMinScore()).isEqualTo(0.55);
        assertThat(paramsCaptor.getValue()).extracting("metadataFilter").isEqualTo(Map.of("itemId", "88"));
        assertThat(req.getConversationId()).isEqualTo(100L);
        verify(ctx.sseChatHelper).chatStreamToSse(eq(req), any(SseEmitter.class), eq(tokenStream),
                eq("token-100"), any());
    }

    @Test
    @DisplayName("智能体对话保存当前消息后只将历史消息传给智能体助手")
    void agentChatPassesPriorContextTurnsToAgentAssistant() {
        TestContext ctx = testContext();
        AskReq req = askReq(ConversationType.GENERAL_AGENT);
        req.setAgentId(40L);
        ConversationTurn currentTurn = turn(7, MessageRole.USER, "继续");
        List<ConversationTurn> history = List.of(
                turn(3, MessageRole.USER, "我叫张三"),
                turn(4, MessageRole.ASSISTANT, "好的，我记住了"));
        ChatAgent agent = ChatAgent.builder()
                .id(40L)
                .modelId(23L)
                .build();
        ModelEntity textModel = textModel(23L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);

        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        ConversationSummary summary = ConversationSummary.builder().summaryContent("Agent 旧摘要").build();
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(summary);
        when(ctx.conversationMessageService.listContextTurns(100L, 7, 10)).thenReturn(history);
        when(ctx.chatAgentService.getById(40L)).thenReturn(agent);
        when(ctx.modelConfigRetriever.getRequiredModel(23L, ModelType.TEXT)).thenReturn(textModel);
        when(ctx.assistantService.createAgentAssistant(agent, textModel, null, summary, history, 20)).thenReturn(assistant);
        when(assistant.chatStreamWithContext(eq(100L), eq("当前问题"), any(InvocationParameters.class)))
                .thenReturn(tokenStream);

        ctx.service.chatStream(req);

        verify(ctx.conversationMessageService).listContextTurns(100L, 7, 10);
        verify(ctx.assistantService).createAgentAssistant(agent, textModel, null, summary, history, 20);
        verify(ctx.sseChatHelper).chatStreamToSse(eq(req), any(SseEmitter.class), eq(tokenStream),
                eq("token-100"), any());
    }

    @Test
    @DisplayName("平台助手通过 InvocationParameters 传递认证层身份并直连原生 Tool Loop")
    void platformAssistantUsesTrustedInvocationParameters() {
        TestContext ctx = testContext();
        final AskReq req = askReq(ConversationType.PLATFORM_AGENT);
        ConversationTurn currentTurn = turn(7, MessageRole.USER, "创建用户");
        currentTurn.setId(700L);
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);
        PlatformAssistantRuntime.Setup setup = new PlatformAssistantRuntime.Setup(List.of(),
                new PromptLayers("平台助手", "用户管理", ""), "用户管理");

        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(null);
        when(ctx.conversationMessageService.listContextTurns(100L, 7, 10)).thenReturn(List.of());
        when(ctx.modelConfigRetriever.getRequiredModel(20L, ModelType.TEXT)).thenReturn(model);
        when(ctx.platformAssistantRuntime.routingCapabilityIndex(any(ToolsetSelectionContext.class)))
                .thenReturn("用户管理");
        when(ctx.platformAssistantRuntime.create(any(HarnessInvocation.class)))
                .thenReturn(setup);
        when(ctx.platformConversationRouter.route(eq(model), eq("当前问题"), eq(null), eq(List.of()),
                eq("用户管理"), eq(false))).thenReturn(PlatformConversationRoute.BUSINESS);
        when(ctx.assistantService.createPlatformAssistant(eq(model), eq(null), eq(List.of()), eq(20), eq(setup),
                any()))
                        .thenReturn(assistant);
        when(assistant.chatStreamWithContext(eq(100L), eq("当前问题"), any(InvocationParameters.class)))
                .thenReturn(tokenStream);

        ctx.service.chatStream(req);

        ArgumentCaptor<InvocationParameters> parametersCaptor = ArgumentCaptor.forClass(InvocationParameters.class);
        verify(assistant).chatStreamWithContext(eq(100L), eq("当前问题"), parametersCaptor.capture());
        verify(ctx.platformConversationRouter).route(eq(model), eq("当前问题"), eq(null), eq(List.of()),
                eq("用户管理"), eq(false));
        HarnessInvocation invocation = HarnessInvocation.from(parametersCaptor.getValue()).orElseThrow();
        ToolsetSelectionContext selection = ToolsetSelectionContext.from(parametersCaptor.getValue()).orElseThrow();
        assertThat(invocation.tenantId()).isEqualTo("2");
        assertThat(invocation.userId()).isEqualTo("1");
        assertThat(invocation.conversationId()).isEqualTo("100");
        assertThat(invocation.turnId()).isEqualTo("700");
        assertThat(invocation.permissions()).containsExactlyInAnyOrder("sys:user:add", "sys:role:assign-users");
        assertThat(selection.route()).isEqualTo("BUSINESS");
        assertThat(selection.taskType()).isEqualTo("PLATFORM_ASSISTANT");
        verify(ctx.sseChatHelper).chatStreamToSse(eq(req), any(SseEmitter.class), eq(tokenStream),
                eq("token-100"), eq(SseChatHelper.ThinkingPolicy.BUSINESS_SAFE), any());
    }

    @Test
    @DisplayName("平台助手将请求级思考和联网开关传入模型配置")
    void platformAssistantAppliesRequestModelOptions() {
        TestContext ctx = testContext();
        AskReq req = askReq(ConversationType.PLATFORM_AGENT);
        req.setReturnThinking(true);
        req.setEnableWebSearch(true);
        ConversationTurn currentTurn = turn(7, MessageRole.USER, "请分析后回答");
        ModelEntity model = textModel(20L);
        ChatAssistant assistant = mock(ChatAssistant.class);
        TokenStream tokenStream = mock(TokenStream.class);
        PlatformAssistantRuntime.Setup setup = new PlatformAssistantRuntime.Setup(List.of(),
                new PromptLayers("平台助手", "用户管理", ""), "用户管理");

        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any())).thenReturn(currentTurn);
        when(ctx.conversationSummaryService.getActiveSummary(100L)).thenReturn(null);
        when(ctx.conversationMessageService.listContextTurns(100L, 7, 10)).thenReturn(List.of());
        when(ctx.modelConfigRetriever.getRequiredModel(20L, ModelType.TEXT)).thenReturn(model);
        when(ctx.platformAssistantRuntime.create(any(HarnessInvocation.class)))
                .thenReturn(setup);
        when(ctx.assistantService.createPlatformAssistant(eq(model), eq(null), eq(List.of()), eq(20), eq(setup),
                any())).thenReturn(assistant);
        when(assistant.chatStreamWithContext(eq(100L), eq("当前问题"), any(InvocationParameters.class)))
                .thenReturn(tokenStream);

        ctx.service.chatStream(req);

        assertThat(model.getReturnThinking()).isTrue();
        assertThat(model.getEnableWebSearch()).isTrue();
    }

    @Test
    @DisplayName("并发首次知识库请求只创建一个会话")
    void concurrentFirstRagRequestsCreateOneConversation() throws Exception {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Conversation.class);
        TestContext ctx = testContext();
        Object distributedLock = new Object();
        when(ctx.redisLockHelper.execute(anyString(), anyLong(), eq(TimeUnit.SECONDS), any()))
                .thenAnswer(invocation -> {
                    synchronized (distributedLock) {
                        Supplier<?> action = invocation.getArgument(3);
                        return action.get();
                    }
                });
        AtomicReference<Conversation> stored = new AtomicReference<>();
        CountDownLatch lookupsReady = new CountDownLatch(2);
        CountDownLatch snapshotsReady = new CountDownLatch(2);
        when(ctx.conversationService.getOne(any())).thenAnswer(invocation -> {
            lookupsReady.countDown();
            lookupsReady.await(300, TimeUnit.MILLISECONDS);
            Conversation snapshot = stored.get();
            snapshotsReady.countDown();
            snapshotsReady.await(300, TimeUnit.MILLISECONDS);
            return snapshot;
        });
        when(ctx.conversationService.save(any(Conversation.class))).thenAnswer(invocation -> {
            Conversation conversation = invocation.getArgument(0);
            conversation.setId(100L);
            stored.set(conversation);
            return true;
        });
        AskReq first = firstRagRequest();
        AskReq second = firstRagRequest();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Conversation> firstResult = executor.submit(() -> {
                start.await();
                return ReflectionTestUtils.invokeMethod(ctx.service, "getOrCreateConversation",
                        1L, first, ConversationType.KNOWLEDGE_BASE);
            });
            Future<Conversation> secondResult = executor.submit(() -> {
                start.await();
                return ReflectionTestUtils.invokeMethod(ctx.service, "getOrCreateConversation",
                        1L, second, ConversationType.KNOWLEDGE_BASE);
            });
            start.countDown();

            assertThat(firstResult.get().getId()).isEqualTo(100L);
            assertThat(secondResult.get().getId()).isEqualTo(100L);
            verify(ctx.conversationService, times(1)).save(any(Conversation.class));
            verify(ctx.redisLockHelper, times(2)).execute(startsWith("ai:conversation:create:"),
                    eq(5L), eq(TimeUnit.SECONDS), any());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("已有会话的知识库不存在时释放处理中占用")
    void missingKnowledgeBaseReleasesExistingConversationReservation() {
        TestContext ctx = testContext();
        AskReq req = askReq(ConversationType.KNOWLEDGE_BASE);
        req.setKbId(30L);
        when(ctx.knowledgeBaseService.getById(30L)).thenReturn(null);

        ctx.service.chatStream(req);

        verify(ctx.sseChatHelper).markConversationComplete("100", "token-100");
    }

    @Test
    @DisplayName("进入模型流前保存用户消息失败时释放处理中占用")
    void userMessageSaveFailureReleasesConversationReservation() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                Conversation.class);
        TestContext ctx = testContext();
        AskReq req = askReq(ConversationType.KNOWLEDGE_BASE);
        req.setKbId(30L);
        KnowledgeBase knowledgeBase = KnowledgeBase.builder().id(30L).build();
        when(ctx.knowledgeBaseService.getById(30L)).thenReturn(knowledgeBase);
        when(ctx.conversationService.getOne(any())).thenReturn(Conversation.builder().id(100L).build());
        when(ctx.conversationMessageService.saveUserMessage(any()))
                .thenThrow(new IllegalStateException("database unavailable"));

        assertThatCode(() -> ctx.service.chatStream(req)).doesNotThrowAnyException();

        verify(ctx.sseChatHelper).markConversationComplete("100", "token-100");
    }

    private static TestContext testContext() {
        AuthenticationContext auth = mock(AuthenticationContext.class);
        SseChatHelper sseChatHelper = mock(SseChatHelper.class);
        ModelConfigRetriever modelConfigRetriever = mock(ModelConfigRetriever.class);
        AssistantService assistantService = mock(AssistantService.class);
        ConversationMessageService conversationMessageService = mock(ConversationMessageService.class);
        ConversationSummaryService conversationSummaryService = mock(ConversationSummaryService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        ChatAgentService chatAgentService = mock(ChatAgentService.class);
        ConversationService conversationService = mock(ConversationService.class);
        RedisLockHelper redisLockHelper = mock(RedisLockHelper.class);
        ConversationContextProperties contextProperties = new ConversationContextProperties();
        PlatformAssistantRuntime platformAssistantRuntime = mock(PlatformAssistantRuntime.class);
        PlatformConversationRouter platformConversationRouter = mock(PlatformConversationRouter.class);
        HarnessOperationStore operationStore = mock(HarnessOperationStore.class);

        when(auth.userId()).thenReturn(1L);
        when(auth.tenantId()).thenReturn(2L);
        when(auth.funcPermissionList()).thenReturn(List.of("sys:user:add", "sys:role:assign-users"));
        when(conversationService.getById(100L))
                .thenReturn(Conversation.builder().id(100L).userId(1L).tenantId(2L).build());
        when(operationStore.findPending(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(platformAssistantRuntime.routingCapabilityIndex(any(ToolsetSelectionContext.class)))
                .thenReturn("用户管理");
        when(platformConversationRouter.route(any(), anyString(), any(), any(), anyString(), anyBoolean()))
                .thenReturn(PlatformConversationRoute.BUSINESS);
        when(sseChatHelper.isDuplicateRequest("100")).thenReturn(false);
        when(sseChatHelper.tryReserveConversation("100")).thenReturn("token-100");
        when(sseChatHelper.createEmitter(any())).thenReturn(new SseEmitter());
        when(redisLockHelper.execute(anyString(), anyLong(), eq(TimeUnit.SECONDS), any()))
                .thenAnswer(invocation -> {
                    Supplier<?> action = invocation.getArgument(3);
                    return action.get();
                });

        org.springframework.core.env.Environment environment = mock(org.springframework.core.env.Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[0]);
        ChatServiceImpl service = new ChatServiceImpl(auth, sseChatHelper, modelConfigRetriever, assistantService,
                conversationMessageService, conversationSummaryService, knowledgeBaseService, chatAgentService,
                conversationService, contextProperties, redisLockHelper,
                platformAssistantRuntime, platformConversationRouter, operationStore,
                () -> new DelegatedAuthorization("test-token"),
                environment);
        return new TestContext(service, sseChatHelper, modelConfigRetriever, assistantService,
                conversationMessageService, conversationSummaryService, knowledgeBaseService, chatAgentService,
                conversationService, redisLockHelper, contextProperties, platformAssistantRuntime,
                platformConversationRouter);
    }

    private static AskReq askReq(ConversationType type) {
        AskReq req = new AskReq();
        req.setChatType(type);
        req.setConversationId(100L);
        req.setModelId("20");
        req.setPrompt("当前问题");
        return req;
    }

    private static AskReq firstRagRequest() {
        AskReq req = askReq(ConversationType.KNOWLEDGE_BASE);
        req.setConversationId(null);
        req.setKbId(30L);
        return req;
    }

    private static ModelEntity textModel(Long id) {
        return ModelEntity.builder()
                .id(id)
                .type(ModelType.TEXT)
                .provider(AiProvider.QWEN)
                .name("qwen-plus")
                .build();
    }

    private static ConversationTurn turn(Integer sequenceNum, MessageRole role, String content) {
        return ConversationTurn.builder()
                .conversationId(100L)
                .sequenceNum(sequenceNum)
                .role(role)
                .userInput(content)
                .modelOutput(content)
                .displayContent(content)
                .build();
    }

    private static final class TestContext {

        private final ChatServiceImpl service;
        private final SseChatHelper sseChatHelper;
        private final ModelConfigRetriever modelConfigRetriever;
        private final AssistantService assistantService;
        private final ConversationMessageService conversationMessageService;
        private final ConversationSummaryService conversationSummaryService;
        private final KnowledgeBaseService knowledgeBaseService;
        private final ChatAgentService chatAgentService;
        private final ConversationService conversationService;
        private final RedisLockHelper redisLockHelper;
        private final ConversationContextProperties contextProperties;
        private final PlatformAssistantRuntime platformAssistantRuntime;
        private final PlatformConversationRouter platformConversationRouter;

        private TestContext(ChatServiceImpl service, SseChatHelper sseChatHelper,
                            ModelConfigRetriever modelConfigRetriever, AssistantService assistantService,
                            ConversationMessageService conversationMessageService,
                            ConversationSummaryService conversationSummaryService,
                            KnowledgeBaseService knowledgeBaseService, ChatAgentService chatAgentService,
                            ConversationService conversationService, RedisLockHelper redisLockHelper,
                            ConversationContextProperties contextProperties,
                            PlatformAssistantRuntime platformAssistantRuntime,
                            PlatformConversationRouter platformConversationRouter) {
            this.service = service;
            this.sseChatHelper = sseChatHelper;
            this.modelConfigRetriever = modelConfigRetriever;
            this.assistantService = assistantService;
            this.conversationMessageService = conversationMessageService;
            this.conversationSummaryService = conversationSummaryService;
            this.knowledgeBaseService = knowledgeBaseService;
            this.chatAgentService = chatAgentService;
            this.conversationService = conversationService;
            this.redisLockHelper = redisLockHelper;
            this.contextProperties = contextProperties;
            this.platformAssistantRuntime = platformAssistantRuntime;
            this.platformConversationRouter = platformConversationRouter;
        }
    }
}
