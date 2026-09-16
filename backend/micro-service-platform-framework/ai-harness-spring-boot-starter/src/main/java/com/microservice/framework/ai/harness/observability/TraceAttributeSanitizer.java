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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Trace 属性脱敏器，递归移除凭证、私有推理和常见敏感业务字段。
 *
 * @author xJh
 * @since 2026-07-18
 */
final class TraceAttributeSanitizer {
    
    private TraceAttributeSanitizer() {
    }
    
    static Map<String, Object> sanitize(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> sanitized = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key != null && !privateKey(key)) {
                sanitized.put(key, sanitizeValue(value));
            }
        });
        return Map.copyOf(sanitized);
    }
    
    private static Object sanitizeValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> nested = new LinkedHashMap<>();
            map.forEach((key, nestedValue) -> {
                if (key != null && !privateKey(String.valueOf(key))) {
                    nested.put(String.valueOf(key), sanitizeValue(nestedValue));
                }
            });
            return Map.copyOf(nested);
        }
        if (value instanceof Collection<?> collection) {
            return collection.stream().map(TraceAttributeSanitizer::sanitizeValue).toList();
        }
        if (value instanceof char[] || value instanceof byte[]) {
            return "[已脱敏]";
        }
        return value;
    }
    
    private static boolean privateKey(String key) {
        String normalized = key.replace("_", "").replace("-", "").toLowerCase(Locale.ROOT);
        return normalized.contains("password") || normalized.contains("passwd") || "pwd".equals(normalized)
                || normalized.contains("token") || normalized.contains("secret")
                || normalized.contains("apikey") || normalized.contains("authorization")
                || normalized.contains("thought") || normalized.contains("reasoning")
                || normalized.contains("chainofthought") || "cot".equals(normalized)
                || normalized.contains("mobile") || normalized.contains("phone")
                || normalized.contains("email");
    }
}
