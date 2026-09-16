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

package com.microservice.platform.ai.core.sse;

import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.domain.dto.result.ChatCompletionResult;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import com.microservice.platform.ai.core.enums.ConversationType;
import dev.langchain4j.community.model.dashscope.QwenChatResponseMetadata;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.output.TokenUsage;
import dev.langchain4j.rag.content.Content;
import dev.langchain4j.rag.content.ContentMetadata;
import dev.langchain4j.service.TokenStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndDeleteParams;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

@DisplayName("SSE 聊天引用和思考事件")
class SseChatHelperReferencesTest {

    @Test
    @DisplayName("Qwen 联网引用只保留答案角标实际采用的搜索结果")
    void qwenReferencesKeepOnlyCitedSearchResults() {
        QwenChatResponseMetadata.SearchResult thirdParty = QwenChatResponseMetadata.SearchResult.builder()
                .index(1)
                .siteName("Third Party")
                .title("第三方资料")
                .url("https://example.com/article")
                .build();
        QwenChatResponseMetadata.SearchResult official = QwenChatResponseMetadata.SearchResult.builder()
                .index(2)
                .siteName("Official")
                .title("官方文档")
                .url("https://official.example/docs")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("官方结论[2]"))
                .metadata(QwenChatResponseMetadata.builder()
                        .searchInfo(QwenChatResponseMetadata.SearchInfo.builder()
                                .searchResults(List.of(thirdParty, official))
                                .build())
                        .build())
                .build();

        List<ChatReference> references = ReflectionTestUtils.invokeMethod(
                SseChatHelper.class, "qwenSearchReferences", response, "官方结论[2]");

        assertThat(references)
                .extracting(ChatReference::title)
                .containsExactly("官方文档");
    }

    @Test
    @DisplayName("正文数字方括号与搜索编号不匹配时保留搜索来源")
    void qwenReferencesIgnoreNonCitationBrackets() {
        QwenChatResponseMetadata.SearchResult result = QwenChatResponseMetadata.SearchResult.builder()
                .index(1)
                .siteName("Official")
                .title("官方文档")
                .url("https://official.example/docs")
                .build();
        ChatResponse response = ChatResponse.builder()
                .aiMessage(AiMessage.from("版本号为[2026]"))
                .metadata(QwenChatResponseMetadata.builder()
                        .searchInfo(QwenChatResponseMetadata.SearchInfo.builder()
                                .searchResults(List.of(result))
                                .build())
                        .build())
                .build();

        List<ChatReference> references = ReflectionTestUtils.invokeMethod(
                SseChatHelper.class, "qwenSearchReferences", response, "版本号为[2026]");

        assertThat(references)
                .extracting(ChatReference::title)
                .containsExactly("官方文档");
    }

    @Test
    @DisplayName("完成结果包含思考内容、RAG 文档引用和 Qwen 联网引用")
    void completionIncludesThinkingAndReferences() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        AtomicReference<ChatCompletionResult> completed = new AtomicReference<>();
        AtomicReference<Boolean> persistedBeforeComplete = new AtomicReference<>(false);

        helper.chatStreamToSse(req, emitter, new ReferenceTokenStream(), null, result -> {
            persistedBeforeComplete.set(!emitter.completed);
            completed.set(result);
        });

        ChatCompletionResult result = completed.get();
        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("最终回答");
        assertThat(result.thinking()).contains("先分析问题");
        assertThat(result.inputTokens()).isEqualTo(3);
        assertThat(result.outputTokens()).isEqualTo(4);
        assertThat(result.references())
                .extracting(ChatReference::type)
                .contains("document", "web");
        assertThat(result.references())
                .anySatisfy(reference -> {
                    assertThat(reference.type()).isEqualTo("document");
                    assertThat(reference.title()).isEqualTo("订单手册");
                    assertThat(reference.chunkId()).isEqualTo("20");
                    assertThat(reference.snippet()).contains("订单规则片段");
                })
                .anySatisfy(reference -> {
                    assertThat(reference.type()).isEqualTo("web");
                    assertThat(reference.title()).isEqualTo("联网资料");
                    assertThat(reference.url()).isEqualTo("https://example.com/news");
                    assertThat(reference.siteName()).isEqualTo("Example");
                });
        assertThat(emitter.completed).isTrue();
        assertThat(persistedBeforeComplete).hasValue(true);
    }

    @Test
    @DisplayName("平台助手向用户展示模型返回的真实思考内容")
    void platformAssistantStreamsModelThinkingContent() {
        final SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        req.setChatType(ConversationType.PLATFORM_AGENT);
        req.setReturnThinking(true);
        AtomicReference<ChatCompletionResult> completed = new AtomicReference<>();

        helper.chatStreamToSse(req, new CapturingSseEmitter(), new PlatformThinkingTokenStream(), null,
                completed::set);

        assertThat(completed.get().thinking())
                .isEqualTo("先确认问题范围，再核对现有数据，最后组织清晰的回答。");
    }

    @Test
    @DisplayName("供应商仅在最终响应返回思考时完成前补发思考事件")
    void finalOnlyThinkingIsSentBeforeComplete() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        helper.chatStreamToSse(req, emitter, new FinalThinkingTokenStream(), null, result -> {
        });

        assertThat(emitter.events)
                .filteredOn(Map.class::isInstance)
                .map(Map.class::cast)
                .anySatisfy(event -> {
                    assertThat(event.get("type")).isEqualTo("thinking");
                    assertThat(event.get("thinking")).isEqualTo("仅在最终响应返回的真实思考");
                });
    }

    @Test
    @DisplayName("业务响应采用关闭策略且不外发 Tool Loop 中间内容和私有思考")
    void businessResponseIsFailClosed() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        AtomicReference<ChatCompletionResult> completed = new AtomicReference<>();

        helper.chatStreamToSse(req, emitter, new BusinessThinkingTokenStream(), null,
                SseChatHelper.ThinkingPolicy.BUSINESS_SAFE, completed::set);

        assertThat(completed.get().content()).isEqualTo("查询完成");
        assertThat(completed.get().thinking()).isNull();
        assertThat(emitter.events.toString())
                .contains("查询完成")
                .doesNotContain("激活相关技能", "thinking", "activate_skill", "iam_search_user", "operationId",
                        "Secret123!", "OP-20260720-1", "内部检索片段", "内部知识库");
    }

    @Test
    @DisplayName("业务最终回复为空时返回中文错误且不保存空消息")
    void emptyBusinessResponseFailsInChinese() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        AtomicReference<ChatCompletionResult> completed = new AtomicReference<>();

        helper.chatStreamToSse(req, emitter, new EmptyBusinessTokenStream(), null,
                SseChatHelper.ThinkingPolicy.BUSINESS_SAFE, completed::set);

        assertThat(completed.get()).isNull();
        assertThat(emitter.events.toString()).contains("业务助手未返回有效回复，请重试");
    }

    @Test
    @DisplayName("完成回调失败时关闭 SSE 而不是发送成功完成事件")
    void completionCallbackFailureClosesEmitter() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        helper.chatStreamToSse(req, emitter, new ReferenceTokenStream(), null, result -> {
            throw new IllegalStateException("database unavailable");
        });

        assertThat(emitter.completed).isTrue();
    }

    @Test
    @DisplayName("模型供应商原始异常转换为中文且不泄露内部参数")
    void modelProviderErrorIsLocalizedAndSanitized() {
        IllegalStateException failure = new IllegalStateException("{\"error\":{\"message\":"
                + "\"Invalid 'tools[1].function.name': expected ^[a-zA-Z0-9_-]+$\","
                + "\"type\":\"invalid_request_error\"}}");

        String message = ReflectionTestUtils.invokeMethod(SseChatHelper.class,
                "modelErrorMessage", failure);

        assertThat(message).isEqualTo("模型暂时无法处理本次请求，请稍后重试");
        assertThat(message).doesNotContain("tools[1]", "invalid_request_error", "function.name");
    }

    @Test
    @DisplayName("并发检查同一会话时只允许一个请求进入")
    void duplicateRequestCheckIsAtomic() throws Exception {
        SseChatHelper helper = new SseChatHelper();
        int requestCount = 32;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> results = java.util.stream.IntStream.range(0, requestCount)
                    .mapToObj(index -> executor.submit(() -> {
                        ready.countDown();
                        start.await();
                        return helper.isDuplicateRequest("100");
                    }))
                    .toList();
            ready.await();
            start.countDown();

            long accepted = 0;
            for (Future<Boolean> result : results) {
                if (!result.get()) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("会话在显式完成前持续阻止后续请求")
    void requestRemainsActiveUntilCompletion() throws Exception {
        SseChatHelper helper = new SseChatHelper();

        String token = helper.tryReserveConversation("100");
        assertThat(token).isNotBlank();
        Thread.sleep(1100);
        assertThat(helper.isDuplicateRequest("100")).isTrue();

        helper.markConversationComplete("100", token);
        assertThat(helper.isDuplicateRequest("100")).isFalse();
    }

    @Test
    @DisplayName("旧请求 token 不得释放当前请求的本地占用")
    void staleTokenDoesNotReleaseCurrentLocalReservation() {
        SseChatHelper helper = new SseChatHelper();

        String token = helper.tryReserveConversation("100");
        helper.markConversationComplete("100", "stale-token");

        assertThat(token).isNotBlank();
        assertThat(helper.isDuplicateRequest("100")).isTrue();
        helper.markConversationComplete("100", token);
        assertThat(helper.isDuplicateRequest("100")).isFalse();
    }

    @Test
    @DisplayName("不同服务实例通过 Redis 共享会话处理中占用")
    @SuppressWarnings("unchecked")
    void processingReservationIsSharedThroughRedis() throws Exception {
        Optional<Constructor<?>> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst();
        assertThat(constructor).as("SseChatHelper should support distributed reservations").isPresent();

        RedissonClient redissonClient = mock(RedissonClient.class);
        final DistributedReservationFixture fixture = distributedReservationFixture(redissonClient);
        SseChatHelper firstInstance = (SseChatHelper) constructor.orElseThrow().newInstance(redissonClient);
        SseChatHelper secondInstance = (SseChatHelper) constructor.orElseThrow().newInstance(redissonClient);

        String token = firstInstance.tryReserveConversation("100");
        assertThat(token).isNotBlank();
        assertThat(secondInstance.isDuplicateRequest("100")).isTrue();
        firstInstance.markConversationComplete("100", token);

        verify(fixture.bucket()).compareAndDelete(any());
    }

    @Test
    @DisplayName("分布式完成持久化期间使用带 TTL 的完成标记")
    void distributedCompletionUsesTtlMarkerUntilPersistenceFinishes() throws Exception {
        Constructor<?> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst()
                .orElseThrow();
        RedissonClient redissonClient = mock(RedissonClient.class);
        DistributedReservationFixture fixture = distributedReservationFixture(redissonClient);
        SseChatHelper helper = (SseChatHelper) constructor.newInstance(redissonClient);
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream stream = new DeferredTokenStream();
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        AtomicInteger persisted = new AtomicInteger();
        String token = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, emitter, stream, token, result -> {
            persistenceStarted.countDown();
            try {
                allowPersistence.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            persisted.incrementAndGet();
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            final Future<?> completion = executor.submit(stream::completeNow);
            assertThat(persistenceStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.redisToken()).hasValue("completing:" + token);
            assertThat(helper.tryReserveConversation("100")).isNull();

            emitter.fireTimeout();
            assertThat(fixture.redisToken()).hasValue("completing:" + token);

            allowPersistence.countDown();
            completion.get();
            assertThat(persisted).hasValue(1);
            assertThat(fixture.redisToken()).hasValue(null);
        } finally {
            allowPersistence.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("分布式状态锁不可用时完成回调关闭落库路径")
    void distributedCompletionFailsClosedWhenStateLockIsUnavailable() throws Exception {
        Constructor<?> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst()
                .orElseThrow();
        RedissonClient redissonClient = mock(RedissonClient.class);
        DistributedReservationFixture fixture = distributedReservationFixture(redissonClient);
        when(fixture.lock().tryLock(
                eq(1L), eq(10L), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(false);
        SseChatHelper helper = (SseChatHelper) constructor.newInstance(redissonClient);
        AskReq req = new AskReq();
        req.setConversationId(100L);
        DeferredTokenStream stream = new DeferredTokenStream();
        AtomicInteger persisted = new AtomicInteger();
        String token = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, new CapturingSseEmitter(), stream, token,
                result -> persisted.incrementAndGet());

        stream.completeNow();

        assertThat(persisted).hasValue(0);
        assertThat(fixture.redisToken()).hasValue(token);
    }

    @Test
    @DisplayName("SSE 超时后释放分布式占用并允许重试")
    @SuppressWarnings("unchecked")
    void emitterTimeoutReleasesDistributedReservation() throws Exception {
        Constructor<?> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst()
                .orElseThrow();
        RedissonClient redissonClient = mock(RedissonClient.class);
        final DistributedReservationFixture fixture = distributedReservationFixture(redissonClient);
        SseChatHelper helper = (SseChatHelper) constructor.newInstance(redissonClient);
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();

        String token = helper.tryReserveConversation("100");
        assertThat(token).isNotBlank();
        helper.chatStreamToSse(req, emitter, new HangingTokenStream(), token, null);
        emitter.fireTimeout();

        assertThat(helper.isDuplicateRequest("100")).isFalse();
        verify(fixture.bucket()).compareAndDelete(any());
    }

    @Test
    @DisplayName("超时旧流迟到完成时不再持久化")
    @SuppressWarnings("unchecked")
    void lateCompletionAfterTimeoutDoesNotPersist() throws Exception {
        Constructor<?> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst()
                .orElseThrow();
        RedissonClient redissonClient = mock(RedissonClient.class);
        distributedReservationFixture(redissonClient);
        SseChatHelper helper = (SseChatHelper) constructor.newInstance(redissonClient);
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream oldStream = new DeferredTokenStream();
        AtomicInteger persisted = new AtomicInteger();

        String oldToken = helper.tryReserveConversation("100");
        assertThat(oldToken).isNotBlank();
        helper.chatStreamToSse(req, emitter, oldStream, oldToken, result -> persisted.incrementAndGet());
        emitter.fireTimeout();
        assertThat(helper.isDuplicateRequest("100")).isFalse();

        oldStream.completeNow();

        assertThat(persisted).hasValue(0);
        assertThat(helper.isDuplicateRequest("100")).isTrue();
    }

    @Test
    @DisplayName("Redis 占用过期并被其他实例续租后旧实例不得持久化迟到响应")
    @SuppressWarnings("unchecked")
    void lateCompletionAfterRedisReservationWasReacquiredDoesNotPersist() throws Exception {
        Constructor<?> constructor = Arrays.stream(SseChatHelper.class.getConstructors())
                .filter(candidate -> Arrays.equals(candidate.getParameterTypes(), new Class<?>[]{RedissonClient.class}))
                .findFirst()
                .orElseThrow();
        RedissonClient redissonClient = mock(RedissonClient.class);
        DistributedReservationFixture fixture = distributedReservationFixture(redissonClient);
        final AtomicReference<String> redisToken = fixture.redisToken();
        SseChatHelper oldInstance = (SseChatHelper) constructor.newInstance(redissonClient);
        final SseChatHelper newInstance = (SseChatHelper) constructor.newInstance(redissonClient);
        AskReq req = new AskReq();
        req.setConversationId(100L);
        DeferredTokenStream oldStream = new DeferredTokenStream();
        AtomicInteger persisted = new AtomicInteger();

        String oldToken = oldInstance.tryReserveConversation("100");
        assertThat(oldToken).isNotBlank();
        oldInstance.chatStreamToSse(req, new CapturingSseEmitter(), oldStream, oldToken,
                result -> persisted.incrementAndGet());
        redisToken.set(null);
        String newToken = newInstance.tryReserveConversation("100");
        assertThat(newToken).isNotBlank();

        oldStream.completeNow();

        assertThat(persisted).hasValue(0);
        assertThat(oldInstance.tryReserveConversation("100")).isNull();
        newInstance.markConversationComplete("100", newToken);
        assertThat(oldInstance.tryReserveConversation("100")).isNotBlank();
    }

    @Test
    @DisplayName("完成持久化期间超时不得允许新请求取得占用")
    void timeoutCannotOpenNewReservationWhileCompletionIsPersisting() throws Exception {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream stream = new DeferredTokenStream();
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        AtomicInteger persisted = new AtomicInteger();
        String token = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, emitter, stream, token, result -> {
            persistenceStarted.countDown();
            try {
                allowPersistence.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            persisted.incrementAndGet();
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> completion = executor.submit(stream::completeNow);
            assertThat(persistenceStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<?> timeout = executor.submit(emitter::fireTimeout);
            for (int attempt = 0; attempt < 50 && !timeout.isDone(); attempt++) {
                Thread.sleep(10);
            }

            assertThat(helper.tryReserveConversation("100")).isNull();

            allowPersistence.countDown();
            completion.get();
            timeout.get();
            assertThat(persisted).hasValue(1);
            assertThat(helper.tryReserveConversation("100")).isNotBlank();
        } finally {
            allowPersistence.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("一个会话持久化时不阻塞哈希碰撞的其他本机会话")
    void completionPersistenceDoesNotBlockUnrelatedLocalConversation() throws Exception {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        DeferredTokenStream stream = new DeferredTokenStream();
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        String firstToken = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, new CapturingSseEmitter(), stream, firstToken, result -> {
            persistenceStarted.countDown();
            try {
                allowPersistence.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        ExecutorService executor = Executors.newSingleThreadExecutor();
        String otherToken = null;
        try {
            final Future<?> completion = executor.submit(stream::completeNow);
            assertThat(persistenceStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();

            otherToken = helper.tryReserveConversation("221");
            assertThat(otherToken).isNotBlank();

            allowPersistence.countDown();
            completion.get();
        } finally {
            allowPersistence.countDown();
            if (otherToken != null) {
                helper.markConversationComplete("221", otherToken);
            }
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("完成持久化已开始时超时回调立即返回且不释放完成占用")
    void timeoutDoesNotWaitForCompletionPersistence() throws Exception {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream stream = new DeferredTokenStream();
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        String token = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, emitter, stream, token, result -> {
            persistenceStarted.countDown();
            try {
                allowPersistence.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> completion = executor.submit(stream::completeNow);
            assertThat(persistenceStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            Future<?> timeout = executor.submit(emitter::fireTimeout);
            Thread.sleep(100);

            assertThat(timeout.isDone()).isTrue();
            assertThat(helper.tryReserveConversation("100")).isNull();

            allowPersistence.countDown();
            completion.get();
            timeout.get();
        } finally {
            allowPersistence.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("完成回调获胜后模型错误不得重复终止 SSE")
    void modelErrorCannotTerminateAfterCompletionWins() throws Exception {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream stream = new DeferredTokenStream();
        CountDownLatch persistenceStarted = new CountDownLatch(1);
        CountDownLatch allowPersistence = new CountDownLatch(1);
        String token = helper.tryReserveConversation("100");
        helper.chatStreamToSse(req, emitter, stream, token, result -> {
            persistenceStarted.countDown();
            try {
                allowPersistence.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            final Future<?> completion = executor.submit(stream::completeNow);
            assertThat(persistenceStarted.await(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            final Future<?> modelError = executor.submit(stream::failNow);
            Thread.sleep(100);

            assertThat(emitter.completeCalls).isZero();

            allowPersistence.countDown();
            completion.get();
            modelError.get();
            assertThat(emitter.completeCalls).isEqualTo(1);
        } finally {
            allowPersistence.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("空 token 不得释放当前本地会话占用")
    void nullTokenDoesNotReleaseTrackedLocalReservation() {
        SseChatHelper helper = new SseChatHelper();
        String token = helper.tryReserveConversation("100");

        helper.markConversationComplete("100", null);

        assertThat(helper.tryReserveConversation("100")).isNull();
        helper.markConversationComplete("100", token);
    }

    @Test
    @DisplayName("本机超时旧流迟到完成时不再持久化或释放新请求")
    void lateLocalCompletionAfterTimeoutDoesNotPersistOrReleaseNewRequest() {
        SseChatHelper helper = new SseChatHelper();
        AskReq req = new AskReq();
        req.setConversationId(100L);
        CapturingSseEmitter emitter = new CapturingSseEmitter();
        DeferredTokenStream oldStream = new DeferredTokenStream();
        AtomicInteger persisted = new AtomicInteger();

        String oldToken = helper.tryReserveConversation("100");
        assertThat(oldToken).isNotBlank();
        helper.chatStreamToSse(req, emitter, oldStream, oldToken, result -> persisted.incrementAndGet());
        emitter.fireTimeout();
        assertThat(helper.isDuplicateRequest("100")).isFalse();

        oldStream.completeNow();

        assertThat(persisted).hasValue(0);
        assertThat(helper.isDuplicateRequest("100")).isTrue();
    }

    @SuppressWarnings("unchecked")
    private static DistributedReservationFixture distributedReservationFixture(RedissonClient redissonClient) throws InterruptedException {
        RBucket<String> bucket = mock(RBucket.class);
        RLock lock = mock(RLock.class);
        AtomicReference<String> redisToken = new AtomicReference<>();
        when(redissonClient.<String>getBucket("ai:conversation:processing:100")).thenReturn(bucket);
        when(redissonClient.getLock("ai:conversation:processing-lock:100")).thenReturn(lock);
        when(lock.tryLock()).thenReturn(true);
        when(lock.tryLock(eq(1L), eq(10L), eq(java.util.concurrent.TimeUnit.SECONDS))).thenReturn(true);
        when(bucket.get()).thenAnswer(invocation -> redisToken.get());
        when(bucket.setIfAbsent(anyString(), eq(java.time.Duration.ofMinutes(5))))
                .thenAnswer(invocation -> redisToken.compareAndSet(null, invocation.getArgument(0)));
        doAnswer(invocation -> {
            redisToken.set(invocation.getArgument(0));
            return null;
        }).when(bucket).set(anyString(), eq(java.time.Duration.ofMinutes(5)));
        when(bucket.compareAndDelete(any())).thenAnswer(invocation -> {
            CompareAndDeleteParams<String> args = invocation.getArgument(0);
            return redisToken.compareAndSet(args.getValue(), null);
        });
        return new DistributedReservationFixture(bucket, lock, redisToken);
    }

    private record DistributedReservationFixture(RBucket<String> bucket, RLock lock,
                                                 AtomicReference<String> redisToken) {
    }

    private static final class CapturingSseEmitter extends SseEmitter {

        private boolean completed;
        private int completeCalls;
        private Runnable timeoutHandler;
        private final List<Object> events = new ArrayList<>();

        @Override
        public synchronized void send(Object object) throws IOException {
            events.add(object);
        }

        @Override
        public synchronized void send(SseEventBuilder builder) throws IOException {
            builder.build().forEach(item -> events.add(item.getData()));
        }

        @Override
        public void complete() {
            completed = true;
            completeCalls++;
        }

        @Override
        public void onTimeout(Runnable callback) {
            timeoutHandler = callback;
        }

        private void fireTimeout() {
            if (timeoutHandler != null) {
                timeoutHandler.run();
            }
        }
    }

    private static class ReferenceTokenStream implements TokenStream {

        private Consumer<String> partialResponse;
        private Consumer<PartialThinking> partialThinking;
        private Consumer<List<Content>> retrieved;
        private Consumer<ChatResponse> complete;
        private Consumer<Throwable> error;

        @Override
        public TokenStream onPartialResponse(Consumer<String> partialResponseHandler) {
            this.partialResponse = partialResponseHandler;
            return this;
        }

        @Override
        public TokenStream onPartialThinking(Consumer<PartialThinking> partialThinkingHandler) {
            this.partialThinking = partialThinkingHandler;
            return this;
        }

        @Override
        public TokenStream onRetrieved(Consumer<List<Content>> retrievedHandler) {
            this.retrieved = retrievedHandler;
            return this;
        }

        @Override
        public TokenStream onToolExecuted(Consumer<dev.langchain4j.service.tool.ToolExecution> toolExecutionHandler) {
            return this;
        }

        @Override
        public TokenStream onCompleteResponse(Consumer<ChatResponse> completeResponseHandler) {
            this.complete = completeResponseHandler;
            return this;
        }

        @Override
        public TokenStream onError(Consumer<Throwable> errorHandler) {
            this.error = errorHandler;
            return this;
        }

        @Override
        public TokenStream ignoreErrors() {
            return this;
        }

        @Override
        public void start() {
            emitPartialThinking(new PartialThinking("先分析问题"));
            emitRetrieved(List.of(Content.from(
                    TextSegment.from("订单规则片段：必须在 24 小时内处理。",
                            Metadata.from(Map.of(
                                    "kbName", "业务知识库",
                                    "itemId", "10",
                                    "chunkId", "20",
                                    "itemTitle", "订单手册",
                                    "docId", "DOC-1"))),
                    Map.of(ContentMetadata.SCORE, 0.91))));
            emitPartialResponse("最终");
            emitPartialResponse("回答");
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder().text("最终回答").thinking("完整思考").build())
                    .metadata(qwenMetadata())
                    .build());
        }

        protected final void emitPartialResponse(String value) {
            partialResponse.accept(value);
        }

        protected final void emitPartialThinking(PartialThinking value) {
            partialThinking.accept(value);
        }

        protected final void emitRetrieved(List<Content> value) {
            retrieved.accept(value);
        }

        protected final void emitComplete(ChatResponse value) {
            complete.accept(value);
        }

        protected final void emitError(Throwable value) {
            error.accept(value);
        }

        private static QwenChatResponseMetadata qwenMetadata() {
            QwenChatResponseMetadata.SearchResult searchResult = QwenChatResponseMetadata.SearchResult.builder()
                    .index(1)
                    .siteName("Example")
                    .title("联网资料")
                    .url("https://example.com/news")
                    .build();
            return QwenChatResponseMetadata.builder()
                    .searchInfo(QwenChatResponseMetadata.SearchInfo.builder()
                            .searchResults(List.of(searchResult))
                            .build())
                    .tokenUsage(new TokenUsage(3, 4))
                    .build();
        }
    }

    private static final class HangingTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
        }
    }

    private static final class PlatformThinkingTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
            emitPartialThinking(new PartialThinking("先确认问题范围，再核对现有数据，最后组织清晰的回答。"));
            emitPartialResponse("查询完成");
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder().text("查询完成").build())
                    .build());
        }
    }

    private static final class FinalThinkingTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
            emitPartialResponse("回答完成");
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder()
                            .text("回答完成")
                            .thinking("仅在最终响应返回的真实思考")
                            .build())
                    .build());
        }
    }

    private static final class BusinessThinkingTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
            emitPartialThinking(new PartialThinking("先确认查询范围，再调用 activate_skill 和 iam_search_user，"));
            emitPartialThinking(new PartialThinking(
                    "原始参数 {\"password\":\"Secret123!\",\"operationId\":\"OP-20260720-1\"}，最后整理结果。"));
            emitRetrieved(List.of(Content.from(TextSegment.from("内部检索片段",
                    Metadata.from(Map.of("kbName", "内部知识库"))))));
            emitPartialResponse("好的，我先激活相关技能来查询用户信息。");
            emitPartialResponse("查询完成");
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder().text("查询完成").build())
                    .build());
        }
    }

    private static final class EmptyBusinessTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
            emitPartialResponse("调用内部能力中。");
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder().text("").build())
                    .build());
        }
    }

    private static final class DeferredTokenStream extends ReferenceTokenStream {

        @Override
        public void start() {
        }

        private void completeNow() {
            emitComplete(ChatResponse.builder()
                    .aiMessage(AiMessage.builder().text("迟到回答").build())
                    .build());
        }

        private void failNow() {
            emitError(new IllegalStateException("late model error"));
        }
    }
}
