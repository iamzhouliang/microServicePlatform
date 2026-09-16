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

package com.microservice.framework.ai.harness.spill;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 将超过阈值的 Tool Result 转换为可审计的安全投影。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class ToolResultSpillProjector {
    
    private final ObjectMapper objectMapper;
    private final HarnessSpillStore store;
    private final int thresholdBytes;
    private final int previewCharacters;
    private final boolean enabled;
    
    public ToolResultSpillProjector(ObjectMapper objectMapper, HarnessSpillStore store,
                                    int thresholdBytes, int previewCharacters) {
        this(objectMapper, store, thresholdBytes, previewCharacters, true);
    }
    
    private ToolResultSpillProjector(ObjectMapper objectMapper, HarnessSpillStore store,
                                     int thresholdBytes, int previewCharacters, boolean enabled) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
        this.store = store;
        this.thresholdBytes = thresholdBytes;
        this.previewCharacters = previewCharacters;
        this.enabled = enabled;
        if (enabled && store == null) {
            throw new IllegalArgumentException("启用 Spill 时存储实现不能为空");
        }
        if (thresholdBytes <= 0 || previewCharacters <= 0) {
            throw new IllegalArgumentException("Spill 阈值和预览长度必须大于零");
        }
    }
    
    /**
     * 创建不外置结果的兼容投影器，供纯单元测试或显式关闭场景使用。
     *
     * @param objectMapper JSON 序列化器
     * @return 仅返回内联结果的投影器
     */
    public static ToolResultSpillProjector inlineOnly(ObjectMapper objectMapper) {
        return new ToolResultSpillProjector(objectMapper, null, Integer.MAX_VALUE, 1_024, false);
    }
    
    public Projection project(String tenantId, String runId, String stepId, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        if (!enabled) {
            return new Projection(safePayload, false);
        }
        final byte[] serialized;
        try {
            serialized = objectMapper.writeValueAsBytes(safePayload);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法序列化 Tool Result", failure);
        }
        if (serialized.length <= thresholdBytes) {
            return new Projection(safePayload, false);
        }
        final HarnessSpillStore.SpillReference reference;
        try {
            reference = store.write(new HarnessSpillStore.SpillWriteRequest(
                    tenantId, runId, stepId, serialized));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Harness 大结果持久化失败，步骤结果未提交", failure);
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("summary", "工具返回结果超过内联阈值，完整内容已安全外置");
        projection.put("preview", "[完整内容已安全外置，未在审计投影中回显]");
        projection.put("spillReference", reference.uri());
        projection.put("contentDigest", reference.contentDigest());
        projection.put("contentSize", reference.contentSize());
        projection.put("expiresAt", reference.expiresAt().toString());
        return new Projection(Map.copyOf(projection), true);
    }
    
    /**
     * 将 LangChain4j 文本结果按同一阈值投影，并返回可供模型和持久化使用的证据指针。
     *
     * @param tenantId 租户标识
     * @param operationId Operation 标识
     * @param toolCallId Tool Call 标识
     * @param resultText Tool 文本结果
     * @return 模型和持久化共用的安全文本投影
     * @throws IllegalStateException 当外置存储或投影序列化失败时抛出
     */
    public TextProjection projectText(String tenantId, String operationId, String toolCallId, String resultText) {
        String safeText = Objects.requireNonNullElse(resultText, "");
        byte[] serialized = safeText.getBytes(StandardCharsets.UTF_8);
        if (!enabled || serialized.length <= thresholdBytes) {
            return new TextProjection(safeText, false, null);
        }
        final HarnessSpillStore.SpillReference reference;
        try {
            reference = store.write(new HarnessSpillStore.SpillWriteRequest(
                    tenantId, operationId, toolCallId, serialized));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Harness 大结果持久化失败，Operation 结果未提交", failure);
        }
        Map<String, Object> projection = new LinkedHashMap<>();
        projection.put("summary", "工具返回结果超过内联阈值，完整内容已安全外置");
        projection.put("spillReference", reference.uri());
        projection.put("contentDigest", reference.contentDigest());
        projection.put("contentSize", reference.contentSize());
        projection.put("expiresAt", reference.expiresAt().toString());
        try {
            return new TextProjection(objectMapper.writeValueAsString(projection), true, reference.uri());
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("无法序列化 Tool Result 安全投影", failure);
        }
    }
    
    /**
     * 持久化和模型可见的结果投影。
     *
     * @param payload 安全投影正文
     * @param spilled 是否已外置完整内容
     */
    public record Projection(Map<String, Object> payload, boolean spilled) {

        public Projection {
            payload = Collections.unmodifiableMap(new LinkedHashMap<>(
                    Objects.requireNonNull(payload, "投影结果不能为空")));
        }
    }
    
    /**
     * 文本 Tool Result 的安全投影。
     *
     * @param resultText 模型可见的安全结果
     * @param spilled 是否已外置完整内容
     * @param evidenceReference 完整结果证据引用
     */
    public record TextProjection(String resultText, boolean spilled, String evidenceReference) {
        public TextProjection {
            resultText = Objects.requireNonNull(resultText, "文本投影不能为空");
        }
    }
}
