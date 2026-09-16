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

import com.microservice.framework.ai.core.constant.AiConstants;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.domain.dto.result.ChatCompletionResult;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import dev.langchain4j.community.model.dashscope.QwenChatResponseMetadata;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.api.bucket.CompareAndDeleteArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SSE 聊天辅助服务
 *
 * @author xJh
 */
@Slf4j
@Component
public class SseChatHelper {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);
    private static final String PROCESSING_KEY_PREFIX = "ai:conversation:processing:";
    private static final String PROCESSING_LOCK_KEY_PREFIX = "ai:conversation:processing-lock:";
    private static final String COMPLETING_TOKEN_PREFIX = "completing:";
    private static final Pattern QWEN_CITATION_PATTERN = Pattern.compile("\\[(?:ref_)?(\\d+)]");
    private static final long STATE_LOCK_WAIT_SECONDS = 1;
    private static final long STATE_LOCK_LEASE_SECONDS = 10;

    /**
     * 引用片段（snippet）最大展示长度，超出部分以省略号截断。
     */
    private static final int SNIPPET_MAX_LENGTH = 240;

    /** 异常中断未释放时的最长占用时间。 */
    private static final long STALE_REQUEST_TIMEOUT_MS = DEFAULT_TIMEOUT.toMillis();

    private final RedissonClient redissonClient;

    private final Map<String, SseEmitter> activeEmitters = new ConcurrentHashMap<>();

    private final Map<String, LocalReservation> localReservations = new ConcurrentHashMap<>();

    public SseChatHelper() {
        this.redissonClient = null;
    }

    @Autowired
    public SseChatHelper(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    /**
     * 检查是否为重复请求
     *
     * @param conversationId 会话ID
     * @return true 如果是重复请求
     */
    public boolean isDuplicateRequest(String conversationId) {
        return tryReserveConversation(conversationId) == null;
    }

    /**
     * 尝试占用会话，成功时返回仅属于当前请求的 token。
     * @param conversationId 会话标识
     * @return 处理结果
     */
    public String tryReserveConversation(String conversationId) {
        if (redissonClient != null) {
            return reserveDistributed(conversationId);
        }
        return reserveLocally(conversationId);
    }

    private String reserveDistributed(String conversationId) {
        RLock lock = redissonClient.getLock(PROCESSING_LOCK_KEY_PREFIX + conversationId);
        boolean locked = false;
        try {
            locked = lock.tryLock();
            if (!locked) {
                log.warn("[SSE] 会话状态正在变更，已忽略重复请求: conversationId={}", conversationId);
                return null;
            }
            RBucket<String> reservation = redissonClient.getBucket(PROCESSING_KEY_PREFIX + conversationId);
            String redisToken = reservation.get();
            if (redisToken != null) {
                log.warn("[SSE] 检测到跨实例重复请求，已忽略: conversationId={}", conversationId);
                return null;
            }
            String token = UUID.randomUUID().toString();
            if (!reservation.setIfAbsent(token, DEFAULT_TIMEOUT)) {
                log.warn("[SSE] 检测到跨实例重复请求，已忽略: conversationId={}", conversationId);
                return null;
            }
            return token;
        } catch (Exception e) {
            log.error("[SSE] 获取会话分布式占用失败: conversationId={}", conversationId, e);
            return null;
        } finally {
            unlockSafely(lock, locked, conversationId);
        }
    }

    private String reserveLocally(String conversationId) {
        long now = System.currentTimeMillis();
        AtomicBoolean duplicate = new AtomicBoolean(false);
        AtomicLong interval = new AtomicLong();
        AtomicReference<String> acquiredToken = new AtomicReference<>();
        localReservations.compute(conversationId, (key, current) -> {
            if (current != null && (now - current.startedAt()) < STALE_REQUEST_TIMEOUT_MS) {
                duplicate.set(true);
                interval.set(Math.max(0L, now - current.startedAt()));
                return current;
            }
            String token = UUID.randomUUID().toString();
            acquiredToken.set(token);
            return new LocalReservation(token, now);
        });

        if (duplicate.get()) {
            log.warn("[SSE] 检测到重复请求，已忽略: conversationId={}, interval={}ms",
                    conversationId, interval.get());
            return null;
        }
        return acquiredToken.get();
    }

    public void markConversationComplete(String conversationId, String reservationToken) {
        releaseConversation(conversationId, reservationToken);
    }

    private void releaseDistributed(String conversationId, String token) {
        if (token == null) {
            return;
        }
        RLock lock = redissonClient.getLock(PROCESSING_LOCK_KEY_PREFIX + conversationId);
        boolean locked = false;
        try {
            locked = lock.tryLock(STATE_LOCK_WAIT_SECONDS, STATE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[SSE] 会话释放锁获取超时: conversationId={}", conversationId);
                return;
            }
            RBucket<String> reservation = redissonClient.getBucket(PROCESSING_KEY_PREFIX + conversationId);
            reservation.compareAndDelete(CompareAndDeleteArgs.expected(token));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SSE] 等待会话释放锁时被中断: conversationId={}", conversationId);
        } catch (Exception e) {
            log.error("[SSE] 释放会话分布式占用失败: conversationId={}", conversationId, e);
        } finally {
            unlockSafely(lock, locked, conversationId);
        }
    }

    /**
     * 创建 SSE 连接
     * @param traceId traceId 参数
     * @return 处理结果
     */
    public SseEmitter createEmitter(String traceId) {
        SseEmitter emitter = new SseEmitter(DEFAULT_TIMEOUT.toMillis());
        activeEmitters.put(traceId, emitter);

        emitter.onCompletion(() -> {
            log.info("[SSE] Completed. traceId={}", traceId);
            activeEmitters.remove(traceId);
        });
        emitter.onTimeout(() -> {
            log.warn("[SSE] Timeout. traceId={}", traceId);
            activeEmitters.remove(traceId);
            safeComplete(emitter);
        });
        emitter.onError(throwable -> {
            log.error("[SSE] Error. traceId={}", traceId, throwable);
            activeEmitters.remove(traceId);
            safeComplete(emitter);
        });

        sendEvent(emitter, AiConstants.SSE_EVENT_START, Map.of("traceId", traceId));
        return emitter;
    }

    /**
     * 将聊天流转换为 SSE 响应。
     *
     * @param askReq 对话请求
     * @param emitter SSE 连接
     * @param tokenStream LangChain4j 模型流
     * @param reservationToken 会话占用令牌
     * @param onComplete 完成后的持久化回调
     */
    public void chatStreamToSse(AskReq askReq, SseEmitter emitter, TokenStream tokenStream,
                                String reservationToken, Consumer<ChatCompletionResult> onComplete) {
        chatStreamToSse(askReq, emitter, tokenStream, reservationToken, ThinkingPolicy.DIRECT, onComplete);
    }

    /**
     * 将模型流转换为 SSE，并按运行时策略投影思考内容。
     *
     * @param askReq 对话请求
     * @param emitter SSE 连接
     * @param tokenStream LangChain4j 模型流
     * @param reservationToken 会话占用令牌
     * @param thinkingPolicy 思考内容对外策略
     * @param onComplete 完成后的持久化回调
     */
    public void chatStreamToSse(AskReq askReq, SseEmitter emitter, TokenStream tokenStream,
                                String reservationToken, ThinkingPolicy thinkingPolicy,
                                Consumer<ChatCompletionResult> onComplete) {
        final ThinkingPolicy effectivePolicy = Objects.requireNonNullElse(thinkingPolicy, ThinkingPolicy.DIRECT);
        final StringBuilder responseBuilder = new StringBuilder();
        final StringBuilder thinkingBuilder = new StringBuilder();
        final List<ChatReference> references = new ArrayList<>();
        final String conversationId = String.valueOf(askReq.getConversationId());
        final AtomicReference<StreamLifecycle> lifecycle = new AtomicReference<>(StreamLifecycle.ACTIVE);
        final Runnable terminateReservation = () -> {
            if (lifecycle.compareAndSet(StreamLifecycle.ACTIVE, StreamLifecycle.TERMINATED)) {
                releaseConversation(conversationId, reservationToken);
            }
        };
        emitter.onTimeout(terminateReservation);
        emitter.onError(error -> terminateReservation.run());
        emitter.onCompletion(terminateReservation);

        tokenStream.onPartialResponse(token -> {
            if (effectivePolicy != ThinkingPolicy.DIRECT) {
                return;
            }
            try {
                responseBuilder.append(token);
                sendMessage(emitter, Map.of("type", AiConstants.SSE_EVENT_PARTIAL, "content", token));
            } catch (IOException e) {
                log.error("Error sending token to SSE", e);
            }
        }).onPartialThinking(thinking -> {
            String text = thinking == null ? null : thinking.text();
            if (text == null || text.isBlank()) {
                return;
            }
            if (effectivePolicy != ThinkingPolicy.DIRECT) {
                return;
            }
            thinkingBuilder.append(text);
            try {
                sendMessage(emitter, Map.of("type", AiConstants.SSE_EVENT_THINKING, "thinking", text));
            } catch (IOException e) {
                log.error("Error sending thinking to SSE", e);
            }
        }).onRetrieved(retrieved -> {
            if (effectivePolicy != ThinkingPolicy.DIRECT) {
                return;
            }
            List<ChatReference> newReferences = toReferences(retrieved);
            if (newReferences.isEmpty()) {
                return;
            }
            List<ChatReference> merged = mergeReferences(references, newReferences);
            if (merged.isEmpty()) {
                return;
            }
            try {
                sendMessage(emitter, Map.of("type", "references", "references", merged));
            } catch (IOException e) {
                log.error("Error sending references to SSE", e);
            }
        }).onCompleteResponse(response -> {
            if (!lifecycle.compareAndSet(StreamLifecycle.ACTIVE, StreamLifecycle.COMPLETING)) {
                log.warn("忽略已失去会话占用的迟到响应: conversationId={}", conversationId);
                return;
            }
            String completionReservationToken = completionToken(reservationToken);
            if (!beginCompletion(conversationId, reservationToken, completionReservationToken)) {
                lifecycle.set(StreamLifecycle.TERMINATED);
                releaseConversation(conversationId, reservationToken);
                log.warn("忽略已失去会话占用的迟到响应: conversationId={}", conversationId);
                return;
            }
            try {
                Integer inputTokens = response.tokenUsage() == null ? 0 : response.tokenUsage().inputTokenCount();
                Integer outputTokens = response.tokenUsage() == null ? 0 : response.tokenUsage().outputTokenCount();
                log.info("模型流式响应完成：conversationId={}, inputTokens={}, outputTokens={}",
                        conversationId, inputTokens, outputTokens);
                String content = effectivePolicy == ThinkingPolicy.BUSINESS_SAFE || responseBuilder.isEmpty()
                        ? responseContent(response)
                        : responseBuilder.toString();
                if (effectivePolicy == ThinkingPolicy.BUSINESS_SAFE && (content == null || content.isBlank())) {
                    sendError(emitter, "业务助手未返回有效回复，请重试");
                    return;
                }
                mergeReferences(references, qwenSearchReferences(response, content));
                String thinking = effectivePolicy.project(thinkingContent(thinkingBuilder, response));
                boolean completionThinkingRequired = effectivePolicy == ThinkingPolicy.BUSINESS_SAFE
                        || thinkingBuilder.isEmpty();
                if (completionThinkingRequired && thinking != null && !thinking.isBlank()) {
                    sendMessage(emitter, Map.of("type", AiConstants.SSE_EVENT_THINKING, "thinking", thinking));
                }
                ChatCompletionResult result = new ChatCompletionResult(
                        content,
                        thinking,
                        references,
                        inputTokens == null ? 0 : inputTokens,
                        outputTokens == null ? 0 : outputTokens);
                if (onComplete != null) {
                    onComplete.accept(result);
                }
                if (effectivePolicy == ThinkingPolicy.BUSINESS_SAFE && content != null && !content.isBlank()) {
                    sendMessage(emitter, Map.of("type", AiConstants.SSE_EVENT_PARTIAL, "content", content));
                }
                if (!references.isEmpty()) {
                    sendMessage(emitter, Map.of("type", "references", "references", references));
                }
                sendMessage(emitter, Map.of("type", AiConstants.SSE_EVENT_COMPLETE));
                emitter.complete();
            } catch (Exception e) {
                log.error("Error completing SSE", e);
                sendError(emitter, "消息处理失败，请重试");
            } finally {
                lifecycle.set(StreamLifecycle.TERMINATED);
                releaseConversation(conversationId, completionReservationToken);
            }
        }).onError(error -> {
            if (!lifecycle.compareAndSet(StreamLifecycle.ACTIVE, StreamLifecycle.TERMINATED)) {
                log.warn("忽略已结束流的迟到模型错误: conversationId={}", conversationId);
                return;
            }
            try {
                log.error("模型流式响应失败: conversationId={}", conversationId, error);
                String message = modelErrorMessage(error);
                emitter.send(SseEmitter.event().name(AiConstants.SSE_EVENT_ERROR)
                        .data(Map.of("type", AiConstants.SSE_EVENT_ERROR, "message", message)));
                emitter.complete();
            } catch (IOException e) {
                log.error("Error sending error to SSE", e);
            } finally {
                releaseConversation(conversationId, reservationToken);
            }
        }).start();
    }

    /**
     * 将模型供应商异常转换为稳定的中文提示，避免向用户泄露请求参数和内部协议。
     * @param error error 参数
     * @return 处理结果
     */
    private static String modelErrorMessage(Throwable error) {
        if (error == null || error.getMessage() == null) {
            return "模型流式响应失败，请稍后重试";
        }
        String message = error.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("429") || message.contains("rate limit") || message.contains("too many requests")) {
            return "模型服务请求过于频繁，请稍后重试";
        }
        if (message.contains("timeout") || message.contains("timed out")) {
            return "模型响应超时，请稍后重试";
        }
        if (message.contains("401") || message.contains("unauthorized") || message.contains("invalid api key")) {
            return "模型服务认证失败，请联系管理员";
        }
        if (message.contains("invalid_request_error") || message.contains("function.name")
                || message.contains("tools[")) {
            return "模型暂时无法处理本次请求，请稍后重试";
        }
        return "模型流式响应失败，请稍后重试";
    }

    private void releaseConversation(String conversationId, String reservationToken) {
        if (reservationToken == null) {
            return;
        }
        if (redissonClient != null) {
            releaseDistributed(conversationId, reservationToken);
            return;
        }
        releaseLocal(conversationId, reservationToken);
    }

    private boolean beginCompletion(String conversationId, String reservationToken,
                                    String completionReservationToken) {
        if (reservationToken == null) {
            return true;
        }
        if (redissonClient != null) {
            return beginDistributedCompletion(conversationId, reservationToken, completionReservationToken);
        }
        AtomicBoolean accepted = new AtomicBoolean(false);
        long now = System.currentTimeMillis();
        localReservations.computeIfPresent(conversationId, (key, current) -> {
            if (!reservationToken.equals(current.token())) {
                return current;
            }
            accepted.set(true);
            return new LocalReservation(completionReservationToken, now);
        });
        return accepted.get();
    }

    private boolean beginDistributedCompletion(String conversationId, String reservationToken,
                                               String completionReservationToken) {
        RLock lock = redissonClient.getLock(PROCESSING_LOCK_KEY_PREFIX + conversationId);
        boolean locked = false;
        try {
            locked = lock.tryLock(STATE_LOCK_WAIT_SECONDS, STATE_LOCK_LEASE_SECONDS, TimeUnit.SECONDS);
            if (!locked) {
                log.warn("[SSE] 会话完成状态锁获取超时: conversationId={}", conversationId);
                return false;
            }
            RBucket<String> reservation = redissonClient.getBucket(PROCESSING_KEY_PREFIX + conversationId);
            if (!reservationToken.equals(reservation.get())) {
                return false;
            }
            reservation.set(completionReservationToken, DEFAULT_TIMEOUT);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[SSE] 等待会话完成状态锁时被中断: conversationId={}", conversationId);
            return false;
        } catch (Exception e) {
            log.error("[SSE] 切换会话完成状态失败: conversationId={}", conversationId, e);
            return false;
        } finally {
            unlockSafely(lock, locked, conversationId);
        }
    }

    private void releaseLocal(String conversationId, String reservationToken) {
        localReservations.computeIfPresent(conversationId, (key, current) -> reservationToken.equals(current.token()) ? null : current);
    }

    private static String completionToken(String reservationToken) {
        return reservationToken == null ? null : COMPLETING_TOKEN_PREFIX + reservationToken;
    }

    private void unlockSafely(RLock lock, boolean locked, String conversationId) {
        if (!locked) {
            return;
        }
        try {
            lock.unlock();
        } catch (Exception e) {
            log.error("[SSE] 释放会话状态锁失败: conversationId={}", conversationId, e);
        }
    }

    private enum StreamLifecycle {
        ACTIVE,
        COMPLETING,
        TERMINATED
    }

    /**
     * 模型输出对外策略。业务 Tool Loop 采用关闭策略，只投影最终回复，不暴露中间内容和私有推理。
     */
    public enum ThinkingPolicy {

        DIRECT,
        BUSINESS_SAFE;

        private String project(String thinking) {
            return this == BUSINESS_SAFE ? null : thinking;
        }
    }

    private record LocalReservation(String token, long startedAt) {
    }

    public void sendError(SseEmitter emitter, String message) {
        sendEvent(emitter, AiConstants.SSE_EVENT_ERROR, message);
        safeComplete(emitter);
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (IOException e) {
            log.warn("Failed to send SSE event: {}", eventName, e);
            safeComplete(emitter);
        }
    }

    private void sendMessage(SseEmitter emitter, Object data) throws IOException {
        if (emitter != null) {
            emitter.send(SseEmitter.event().data(data));
        }
    }

    private static String responseContent(ChatResponse response) {
        AiMessage message = response == null ? null : response.aiMessage();
        String text = message == null ? null : message.text();
        return text == null ? "" : text;
    }

    private static String thinkingContent(StringBuilder partialThinking, ChatResponse response) {
        if (!partialThinking.isEmpty()) {
            return partialThinking.toString();
        }
        AiMessage message = response == null ? null : response.aiMessage();
        if (message != null && message.thinking() != null && !message.thinking().isBlank()) {
            return message.thinking();
        }
        return response == null ? null : reflectedString(response.metadata(), "reasoningContent");
    }

    private static List<ChatReference> toReferences(List<dev.langchain4j.rag.content.Content> contents) {
        if (contents == null || contents.isEmpty()) {
            return List.of();
        }
        return contents.stream()
                .map(SseChatHelper::toReference)
                .filter(Objects::nonNull)
                .toList();
    }

    private static ChatReference toReference(dev.langchain4j.rag.content.Content content) {
        if (content == null || content.textSegment() == null) {
            return null;
        }
        TextSegment segment = content.textSegment();
        Metadata metadata = segment.metadata();
        Map<String, Object> metadataMap = metadata == null ? Map.of() : metadata.toMap();
        String url = firstString(metadataMap, "url", "sourceUrl", "source_url", "webUrl", "link");
        String type = isHttpUrl(url) ? "web" : "document";
        String itemTitle = firstString(metadataMap, "itemTitle", "title", "fileName", "filename", "name");
        String documentId = firstString(metadataMap, "docId", "documentId", "sourceId");
        String documentName = firstString(metadataMap, "documentName", "fileName", "filename", "itemTitle", "title");
        String title = itemTitle != null ? itemTitle : defaultTitle(type, documentId);
        Double score = content.metadata() == null ? null : score(content.metadata());
        return new ChatReference(
                type,
                title,
                url,
                firstString(metadataMap, "siteName", "site_name", "sourceName"),
                firstString(metadataMap, "icon", "favicon"),
                documentName,
                documentId,
                firstString(metadataMap, "itemId", "ItemId"),
                firstString(metadataMap, "chunkId", "ChunkId"),
                score,
                snippet(segment.text()),
                metadataMap);
    }

    private static List<ChatReference> qwenSearchReferences(ChatResponse response, String content) {
        if (response == null || !(response.metadata()instanceof QwenChatResponseMetadata metadata)
                || metadata.searchInfo() == null || metadata.searchInfo().searchResults() == null) {
            return List.of();
        }
        List<QwenChatResponseMetadata.SearchResult> searchResults = metadata.searchInfo().searchResults().stream()
                .filter(Objects::nonNull)
                .toList();
        Set<Integer> citedIndexes = qwenCitationIndexes(content);
        Set<Integer> matchedIndexes = new LinkedHashSet<>();
        for (QwenChatResponseMetadata.SearchResult result : searchResults) {
            if (citedIndexes.contains(result.index())) {
                matchedIndexes.add(result.index());
            }
        }
        return searchResults.stream()
                .filter(result -> matchedIndexes.isEmpty() || matchedIndexes.contains(result.index()))
                .map(result -> new ChatReference(
                        "web",
                        result.title(),
                        result.url(),
                        result.siteName(),
                        result.icon(),
                        null,
                        null,
                        null,
                        null,
                        result.index() == null ? null : result.index().doubleValue(),
                        null,
                        Map.of("index", result.index() == null ? "" : result.index())))
                .filter(reference -> reference.url() != null && !reference.url().isBlank())
                .toList();
    }

    private static Set<Integer> qwenCitationIndexes(String content) {
        if (content == null || content.isBlank()) {
            return Set.of();
        }
        Set<Integer> indexes = new LinkedHashSet<>();
        Matcher matcher = QWEN_CITATION_PATTERN.matcher(content);
        while (matcher.find()) {
            indexes.add(Integer.valueOf(matcher.group(1)));
        }
        return indexes;
    }

    private static List<ChatReference> mergeReferences(List<ChatReference> target, List<ChatReference> incoming) {
        if (incoming == null || incoming.isEmpty()) {
            return List.of();
        }
        Set<String> keys = new LinkedHashSet<>(target.stream().map(ChatReference::dedupeKey).toList());
        List<ChatReference> added = new ArrayList<>();
        for (ChatReference reference : incoming) {
            if (reference == null || !keys.add(reference.dedupeKey())) {
                continue;
            }
            target.add(reference);
            added.add(reference);
        }
        return added;
    }

    private static String firstString(Map<String, Object> metadata, String... keys) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null && !String.valueOf(value).isBlank()) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    private static String defaultTitle(String type, String documentId) {
        if ("web".equals(type)) {
            return "联网资料";
        }
        return documentId == null || documentId.isBlank() ? "知识片段" : "文档 " + documentId;
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= SNIPPET_MAX_LENGTH
                ? normalized
                : normalized.substring(0, SNIPPET_MAX_LENGTH) + "...";
    }

    private static Double score(Map<dev.langchain4j.rag.content.ContentMetadata, Object> metadata) {
        Object score = metadata.get(dev.langchain4j.rag.content.ContentMetadata.RERANKED_SCORE);
        if (score == null) {
            score = metadata.get(dev.langchain4j.rag.content.ContentMetadata.SCORE);
        }
        if (score instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static String reflectedString(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(methodName).invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private void safeComplete(SseEmitter emitter) {
        if (emitter == null) {
            return;
        }
        try {
            emitter.complete();
        } catch (Exception e) {
            log.debug("关闭 SSE 失败", e);
        }
    }

    public int getActiveCount() {
        return activeEmitters.size();
    }

    public boolean isActive(String traceId) {
        return activeEmitters.containsKey(traceId);
    }
}
