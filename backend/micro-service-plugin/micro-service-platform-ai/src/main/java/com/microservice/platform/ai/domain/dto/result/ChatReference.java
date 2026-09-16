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

package com.microservice.platform.ai.domain.dto.result;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import lombok.Value;
import lombok.experimental.Accessors;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * AI 回答引用来源.
 */
@Value
@Accessors(fluent = true)
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class ChatReference {

    public static final String VARIABLES_KEY = "references";

    private final String type;
    private final String title;
    private final String url;
    private final String siteName;
    private final String icon;
    private final String documentName;
    private final String documentId;
    private final String itemId;
    private final String chunkId;
    private final Double score;
    private final String snippet;
    private final Map<String, Object> metadata;

    public ChatReference(String type, String title, String url, String siteName, String icon, String documentName,
                         String documentId, String itemId, String chunkId, Double score, String snippet,
                         Map<String, Object> metadata) {
        this.type = type;
        this.title = title;
        this.url = url;
        this.siteName = siteName;
        this.icon = icon;
        this.documentName = documentName;
        this.documentId = documentId;
        this.itemId = itemId;
        this.chunkId = chunkId;
        this.score = score;
        this.snippet = snippet;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        putIfPresent(map, "type", type);
        putIfPresent(map, "title", title);
        putIfPresent(map, "url", url);
        putIfPresent(map, "siteName", siteName);
        putIfPresent(map, "icon", icon);
        putIfPresent(map, "documentName", documentName);
        putIfPresent(map, "documentId", documentId);
        putIfPresent(map, "itemId", itemId);
        putIfPresent(map, "chunkId", chunkId);
        putIfPresent(map, "score", score);
        putIfPresent(map, "snippet", snippet);
        if (metadata != null && !metadata.isEmpty()) {
            map.put("metadata", metadata);
        }
        return map;
    }

    public String dedupeKey() {
        if (url != null && !url.isBlank()) {
            return "web:" + url;
        }
        if (chunkId != null && !chunkId.isBlank()) {
            return "chunk:" + chunkId;
        }
        if (documentId != null && !documentId.isBlank()) {
            return "doc:" + documentId;
        }
        return type + ":" + Objects.toString(title, "") + ":" + Objects.toString(snippet, "");
    }

    public static List<ChatReference> fromVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return List.of();
        }
        Object raw = variables.get(VARIABLES_KEY);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .map(ChatReference::fromObject)
                .filter(Objects::nonNull)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private static ChatReference fromObject(Object value) {
        if (value instanceof ChatReference reference) {
            return reference;
        }
        if (!(value instanceof Map<?, ?> rawMap)) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        rawMap.forEach((key, val) -> map.put(String.valueOf(key), val));
        Map<String, Object> metadata = map.get("metadata")instanceof Map<?, ?> metadataMap
                ? toStringKeyMap(metadataMap)
                : Map.of();
        return new ChatReference(
                stringValue(map.get("type")),
                stringValue(map.get("title")),
                stringValue(map.get("url")),
                stringValue(map.get("siteName")),
                stringValue(map.get("icon")),
                stringValue(map.get("documentName")),
                stringValue(map.get("documentId")),
                stringValue(map.get("itemId")),
                stringValue(map.get("chunkId")),
                doubleValue(map.get("score")),
                stringValue(map.get("snippet")),
                metadata);
    }

    private static Map<String, Object> toStringKeyMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
