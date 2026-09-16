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

package com.microservice.framework.ai.harness.observability;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 不包含私有思维链的 Harness 结构化 Trace 事件。
 *
 * @param runId 运行标识
 * @param stepId Tool 调用标识
 * @param type 事件类型
 * @param status 执行状态
 * @param occurredAt 发生时间
 * @param durationMillis 耗时毫秒数
 * @param estimatedTokens 近似 Token 数
 * @param evidenceReferences 证据引用列表
 * @param attributes 脱敏后的观测属性
 * @author xJh
 * @since 2026-07-18
 */
// Trace 属性是稳定观测契约，保持扁平结构便于指标适配与序列化。
// CHECKSTYLE:OFF
public record HarnessTraceEvent(
        String runId,
        String stepId,
        String type,
        String status,
        Instant occurredAt,
        long durationMillis,
        int estimatedTokens,
        List<String> evidenceReferences,
        Map<String, Object> attributes) {
    // CHECKSTYLE:ON

    public HarnessTraceEvent {
        runId = requireText(runId, "运行标识");
        type = requireText(type, "Trace 类型");
        status = requireText(status, "Trace 状态");
        occurredAt = Objects.requireNonNull(occurredAt, "Trace 时间不能为空");
        if (durationMillis < 0 || estimatedTokens < 0) {
            throw new IllegalArgumentException("Trace 耗时和 Token 估算不能为负数");
        }
        evidenceReferences = evidenceReferences == null ? List.of() : evidenceReferences.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(HarnessTraceEvent::isSafeEvidenceReference)
                .toList();
        attributes = sanitizeAttributes(attributes);
    }

    public static HarnessTraceEvent create(String runId, String stepId, String type, String status,
            Instant occurredAt, Duration duration, int estimatedTokens, List<String> evidenceReferences,
            Map<String, Object> attributes) {
        long durationMillis = duration == null ? 0 : Math.max(0, duration.toMillis());
        return new HarnessTraceEvent(runId, stepId, type, status, occurredAt, durationMillis,
                Math.max(0, estimatedTokens), evidenceReferences, attributes);
    }

    private static Map<String, Object> sanitizeAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> allowed = new LinkedHashMap<>();
        attributes.forEach((key, value) -> {
            if (key != null && !isPrivateKey(key)) {
                allowed.put(key, value);
            }
        });
        return TraceAttributeSanitizer.sanitize(allowed);
    }

    private static boolean isPrivateKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("passwd") || "pwd".equals(normalized)
                || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("authorization")
                || normalized.contains("thought") || normalized.contains("reasoning")
                || normalized.contains("chainofthought") || "cot".equals(normalized);
    }

    private static boolean isSafeEvidenceReference(String reference) {
        return reference.startsWith("spill://") || reference.startsWith("event://")
                || reference.startsWith("run://");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
