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

package com.microservice.platform.ai.integration;

import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.framework.redis.plus.lock.RedisLockHelper;
import com.microservice.platform.ai.AiApplication;
import com.microservice.platform.ai.core.assistant.interfaces.ChatAssistant;
import com.microservice.platform.ai.core.assistant.service.AssistantService;
import com.microservice.platform.ai.core.config.ConversationContextProperties;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.core.enums.ConversationType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.sse.SseChatHelper;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.ChatAgentService;
import com.microservice.platform.ai.service.ConversationMessageService;
import com.microservice.platform.ai.service.ConversationService;
import com.microservice.platform.ai.service.ConversationSummaryService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.ModelService;
import com.microservice.platform.ai.service.impl.ChatServiceImpl;
import dev.langchain4j.community.model.dashscope.QwenChatResponseMetadata;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Qwen 真实模型上下文冒烟测试.
 * 默认跳过，避免普通测试消耗外部模型额度；显式传入 {@code -Dlive.qwen=true} 时才会连接本地配置的数据源并调用真实 Qwen。
 *
 * @author xiao1
 * @since 2026-07
 */
@SpringBootTest(classes = AiApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false"
        })
@EnabledIfSystemProperty(named = "live.qwen", matches = "true")
@DisplayName("Qwen 真实模型上下文冒烟测试")
class LiveQwenContextSmokeTest {

    private static final String MARKER = "BLUE-73";

    @Autowired
    private ModelService modelService;

    @Autowired
    private AssistantService assistantService;

    @Autowired
    private RedisLockHelper redisLockHelper;

    @Autowired
    private ModelConfigRetriever modelConfigRetriever;

    @Autowired
    private ConversationMessageService conversationMessageService;

    @Autowired
    private ConversationSummaryService conversationSummaryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("真实 Qwen 调用能读取摘要和最近上下文")
    void liveQwenReadsInjectedSummaryAndRecentHistory() throws InterruptedException {
        Assumptions.assumeTrue(Boolean.getBoolean("live.qwen"), "Set -Dlive.qwen=true to run live Qwen smoke test");

        ModelEntity model = latestQwenTextModel();
        model.setEnableWebSearch(false);
        model.setReturnThinking(false);

        ConversationSummary summary = ConversationSummary.builder()
                .summaryContent("历史摘要：用户的校验码是 " + MARKER + "。")
                .build();
        List<ConversationTurn> history = List.of(
                turn(MessageRole.USER, "请记住我的校验码：" + MARKER),
                turn(MessageRole.ASSISTANT, "已记住，校验码是 " + MARKER));

        ChatAssistant assistant = assistantService.createMemoryAssistant(model, summary, history, 4);
        String response = awaitResponse(assistant.chatStream(System.nanoTime(),
                "只根据前面的会话回答：我的校验码是什么？请只输出校验码。"), Duration.ofSeconds(90));

        assertThat(response).isNotBlank();
        assertThat(response.replace(" ", "").toUpperCase(Locale.ROOT)).contains(MARKER);
    }

    @Test
    @DisplayName("真实 Qwen 联网搜索返回可展示的网页引用")
    void liveQwenWebSearchReturnsSearchReferences() throws InterruptedException {
        Assumptions.assumeTrue(Boolean.getBoolean("live.qwen"), "Set -Dlive.qwen=true to run live Qwen smoke test");

        ModelEntity model = latestQwenTextModel();
        model.setEnableWebSearch(true);
        model.setReturnThinking(false);

        ChatAssistant assistant = assistantService.createMemoryAssistant(model);
        LiveResponse liveResponse = awaitLiveResponse(assistant.chatStream(System.nanoTime(),
                "请联网搜索 OpenAI 官方网站，并用一句中文回答官方网站域名是什么。"), Duration.ofSeconds(90));

        assertThat(liveResponse.content()).isNotBlank();
        assertThat(liveResponse.response().metadata()).isInstanceOf(QwenChatResponseMetadata.class);
        QwenChatResponseMetadata metadata = (QwenChatResponseMetadata) liveResponse.response().metadata();
        assertThat(metadata.searchInfo()).as("Qwen search metadata should be present").isNotNull();
        assertThat(metadata.searchInfo().searchResults())
                .as("Qwen web search should return website references")
                .isNotEmpty()
                .anySatisfy(reference -> {
                    assertThat(reference.title()).isNotBlank();
                    assertThat(reference.url()).startsWith("http");
                });
    }

    @Test
    @DisplayName("真实 chatStream 入口能携带摘要和最近上下文回答")
    void liveChatStreamEntryReadsPersistedContext() throws InterruptedException {
        Assumptions.assumeTrue(Boolean.getBoolean("live.qwen"), "Set -Dlive.qwen=true to run live Qwen smoke test");

        final Long conversationId = 9_876_000_000L + Math.floorMod(System.nanoTime(), 1_000_000L);
        ModelEntity model = latestQwenTextModel();
        model.setEnableWebSearch(false);
        model.setReturnThinking(false);

        AuthenticationContext auth = mock(AuthenticationContext.class);
        final TestSseChatHelper sseChatHelper = new TestSseChatHelper();
        final KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        final ChatAgentService chatAgentService = mock(ChatAgentService.class);
        final ConversationService conversationService = mock(ConversationService.class);
        ConversationContextProperties contextProperties = new ConversationContextProperties();
        contextProperties.setRecentRounds(2);

        ConversationSummary summary = ConversationSummary.builder()
                .conversationId(conversationId)
                .userId(1L)
                .tenantId(2L)
                .summaryContent("历史摘要：用户的校验码是 " + MARKER + "。")
                .coveredUntilSequenceNum(6)
                .version(1)
                .createTime(Instant.now())
                .deleted(false)
                .build();
        List<ConversationTurn> history = List.of(
                persistedTurn(conversationId, 7, MessageRole.USER, "请记住我的校验码：" + MARKER),
                persistedTurn(conversationId, 8, MessageRole.ASSISTANT, "已记住，校验码是 " + MARKER));

        when(auth.userId()).thenReturn(1L);
        when(auth.tenantId()).thenReturn(2L);

        ensureSummaryTableExists();
        cleanupConversation(conversationId);

        ChatServiceImpl chatService = new ChatServiceImpl(auth, sseChatHelper, modelConfigRetriever, assistantService,
                conversationMessageService, conversationSummaryService, knowledgeBaseService, chatAgentService,
                conversationService, contextProperties, redisLockHelper,
                org.mockito.Mockito.mock(com.microservice.platform.ai.core.assistant.service.PlatformAssistantRuntime.class),
                org.mockito.Mockito.mock(com.microservice.platform.ai.core.assistant.routing.PlatformConversationRouter.class),
                org.mockito.Mockito.mock(com.microservice.framework.ai.harness.runtime.HarnessOperationStore.class),
                () -> new com.microservice.framework.ai.harness.runtime.DelegatedAuthorization("test-token"),
                org.mockito.Mockito.mock(org.springframework.core.env.Environment.class));
        AskReq req = new AskReq();
        req.setChatType(ConversationType.NORMAL_TEXT);
        req.setConversationId(conversationId);
        req.setModelId(String.valueOf(model.getId()));
        req.setEnableWebSearch(false);
        req.setReturnThinking(false);
        req.setPrompt("只根据本轮之前的会话上下文回答：我的校验码是什么？请只输出校验码。");

        try {
            conversationSummaryService.save(summary);
            conversationMessageService.saveBatch(history);
            chatService.chatStream(req);

            ConversationTurn assistantTurn = awaitAssistantTurn(conversationId, sseChatHelper, Duration.ofSeconds(90));
            assertThat(sseChatHelper.error()).as("live chatStream should not emit an SSE error").isNull();
            assertThat(assistantTurn).isNotNull();
            assertThat(assistantTurn.getModelOutput().replace(" ", "").toUpperCase(Locale.ROOT)).contains(MARKER);
            assertThat(conversationMessageService.listContextTurns(conversationId, 9, 2))
                    .extracting(ConversationTurn::getSequenceNum)
                    .containsExactly(7, 8);
            assertThat(conversationSummaryService.getActiveSummary(conversationId).getSummaryContent())
                    .contains(MARKER);
        } finally {
            cleanupConversation(conversationId);
        }
    }

    private ModelEntity latestQwenTextModel() {
        ModelEntity model = modelService.getOne(Wraps.<ModelEntity>lbQ()
                .eq(ModelEntity::getProvider, AiProvider.QWEN)
                .eq(ModelEntity::getType, ModelType.TEXT)
                .isNotNull(ModelEntity::getApiKey)
                .orderByDesc(ModelEntity::getLastModifyTime)
                .last("LIMIT 1"));
        assertThat(model).as("database should contain an enabled Qwen text model").isNotNull();
        return model;
    }

    private static ConversationTurn turn(MessageRole role, String content) {
        return ConversationTurn.builder()
                .role(role)
                .userInput(role == MessageRole.USER ? content : null)
                .modelOutput(role == MessageRole.ASSISTANT ? content : null)
                .displayContent(content)
                .build();
    }

    private static ConversationTurn persistedTurn(Long conversationId, int sequenceNum, MessageRole role,
                                                  String content) {
        return ConversationTurn.builder()
                .conversationId(conversationId)
                .userId(1L)
                .tenantId(2L)
                .role(role)
                .userInput(role == MessageRole.USER ? content : null)
                .modelOutput(role == MessageRole.ASSISTANT ? content : null)
                .displayContent(content)
                .inputTokens(0)
                .outputTokens(0)
                .sequenceNum(sequenceNum)
                .createTime(Instant.now())
                .deleted(false)
                .build();
    }

    private static String awaitResponse(TokenStream tokenStream, Duration timeout) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder content = new StringBuilder();
        AtomicReference<Throwable> error = new AtomicReference<>();
        tokenStream.onPartialResponse(content::append)
                .onCompleteResponse(response -> latch.countDown())
                .onError(throwable -> {
                    error.set(throwable);
                    latch.countDown();
                })
                .start();

        boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(completed).as("live Qwen stream should finish within %s", timeout).isTrue();
        assertThat(error.get()).as("live Qwen stream should not fail").isNull();
        return content.toString();
    }

    private static LiveResponse awaitLiveResponse(TokenStream tokenStream, Duration timeout) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        StringBuilder content = new StringBuilder();
        AtomicReference<ChatResponse> response = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        tokenStream.onPartialResponse(content::append)
                .onCompleteResponse(chatResponse -> {
                    response.set(chatResponse);
                    latch.countDown();
                })
                .onError(throwable -> {
                    error.set(throwable);
                    latch.countDown();
                })
                .start();

        boolean completed = latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        assertThat(completed).as("live Qwen stream should finish within %s", timeout).isTrue();
        assertThat(error.get()).as("live Qwen stream should not fail").isNull();
        assertThat(response.get()).as("live Qwen stream should complete with metadata").isNotNull();
        return new LiveResponse(content.toString(), response.get());
    }

    private ConversationTurn awaitAssistantTurn(Long conversationId, TestSseChatHelper sseChatHelper, Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            assertThat(sseChatHelper.error()).as("live chatStream should not emit an SSE error").isNull();
            List<ConversationTurn> turns = conversationMessageService.list(Wraps.<ConversationTurn>lbQ()
                    .eq(ConversationTurn::getConversationId, conversationId)
                    .eq(ConversationTurn::getRole, MessageRole.ASSISTANT)
                    .gt(ConversationTurn::getSequenceNum, 8)
                    .orderByDesc(ConversationTurn::getSequenceNum)
                    .last("LIMIT 1"));
            if (!turns.isEmpty()) {
                return turns.getFirst();
            }
            Thread.sleep(200);
        }
        return null;
    }

    private void cleanupConversation(Long conversationId) {
        conversationMessageService.remove(Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, conversationId));
        conversationSummaryService.remove(Wraps.<ConversationSummary>lbQ()
                .eq(ConversationSummary::getConversationId, conversationId));
    }

    private void ensureSummaryTableExists() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS `ai_conversation_summary` (
                    `id` bigint NOT NULL COMMENT '主键ID',
                    `conversation_id` bigint NOT NULL COMMENT '会话ID',
                    `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
                    `summary_content` text NOT NULL COMMENT '摘要内容',
                    `covered_until_sequence_num` int NOT NULL DEFAULT '0' COMMENT '摘要覆盖到的会话内序号',
                    `model_name` varchar(128) DEFAULT NULL COMMENT '摘要模型名称',
                    `version` int NOT NULL DEFAULT '1' COMMENT '摘要版本',
                    `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
                    `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
                    `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
                    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
                    `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
                    `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
                    `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
                    PRIMARY KEY (`id`),
                    UNIQUE KEY `uk_conversation_summary_conversation` (`conversation_id`),
                    KEY `idx_conversation_summary_user` (`user_id`),
                    KEY `idx_conversation_summary_tenant` (`tenant_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI会话摘要'
                """);
    }

    private record LiveResponse(String content, ChatResponse response) {
    }

    private static final class TestSseChatHelper extends SseChatHelper {

        private final AtomicReference<Object> error = new AtomicReference<>();

        @Override
        public SseEmitter createEmitter(String traceId) {
            return new CapturingSseEmitter(error);
        }

        Object error() {
            return error.get();
        }
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private final AtomicReference<Object> error;

        CapturingSseEmitter(AtomicReference<Object> error) {
            super(0L);
            this.error = error;
        }

        @Override
        public synchronized void send(Object object) {
            if (object instanceof Throwable) {
                error.set(object);
            }
        }

        @Override
        public synchronized void send(SseEmitter.SseEventBuilder builder) {
            // no-op: this test verifies the persisted completion callback, not the HTTP transport.
        }

        @Override
        public void completeWithError(Throwable ex) {
            error.set(ex);
        }
    }
}
