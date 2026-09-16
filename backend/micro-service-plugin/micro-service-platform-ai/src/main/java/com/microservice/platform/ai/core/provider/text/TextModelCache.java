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

package com.microservice.platform.ai.core.provider.text;

import cn.hutool.extra.spring.SpringUtil;
import com.microservice.framework.ai.core.provider.text.TextModelProvider;
import com.microservice.framework.ai.core.provider.text.TextModelProviderRegistry;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 文本模型缓存
 *
 * @author xJh
 * @since 2025/10/11
 */
@Component
public class TextModelCache {

    @Cacheable(value = "chatModels",
            key = "T(com.microservice.platform.ai.core.provider.text.TextModelCache).generateCacheKey(#config)")
    public ChatModel getModel(ModelEntity config) {
        return getProvider(config).createModel(config);
    }

    @Cacheable(value = "streamingChatModels",
            key = "T(com.microservice.platform.ai.core.provider.text.TextModelCache).generateCacheKey(#config)")
    public StreamingChatModel getStreamModel(ModelEntity config) {
        return getProvider(config).createStreamingModel(config);
    }

    private TextModelProvider getProvider(ModelEntity config) {
        return SpringUtil.getBean(TextModelProviderRegistry.class).getProvider(config);
    }

    /**
     * 生成缓存 key
     * @param config 节点配置
     * @return 处理结果
     */
    public static String generateCacheKey(ModelEntity config) {
        Objects.requireNonNull(config, "config");
        String provider = value(config.getProviderCode(), "unknown");
        String model = value(config.getName(), "unknown");
        String fingerprintSource = String.join("\0",
                provider,
                value(config.getModelType(), "unknown"),
                model,
                value(config.getBaseUrl(), "default"),
                value(config.getApiKey(), ""),
                String.valueOf(Boolean.TRUE.equals(config.getEnableWebSearch())),
                String.valueOf(Boolean.TRUE.equals(config.getReturnThinking())),
                canonicalVariables(config.getVariables()));
        return "v2:%s:%s:%s".formatted(provider, model, sha256(fingerprintSource));
    }

    private static String canonicalVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return "";
        }
        return variables.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.nullsFirst(String::compareTo)))
                .map(entry -> value(entry.getKey(), "<null>") + "=" + String.valueOf(entry.getValue()))
                .collect(Collectors.joining("&"));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String value(String value, String defaultValue) {
        return value == null ? defaultValue : value;
    }
}
