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

package com.microservice.platform.ai.core.provider.scoring;

import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.model.scoring.ScoringModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 重排序模型服务
 * 提供重排序模型的获取和缓存管理
 *
 * @author xJh
 * @since 2025/12/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoringModelService {

    private final ScoringModelProviderRegistry registry;

    /**
     * 模型缓存，避免重复创建
     */
    private final Map<String, ScoringModel> modelCache = new ConcurrentHashMap<>();

    /**
     * 根据配置获取 ScoringModel 实例
     *
     * @param config 模型配置
     * @return ScoringModel 实例
     */
    public ScoringModel getModel(ModelEntity config) {
        String cacheKey = buildCacheKey(config);
        return modelCache.computeIfAbsent(cacheKey, key -> {
            ScoringModelProvider provider = registry.getProvider(config);
            log.debug("创建重排序模型: provider={}, model={}", config.getProvider(), config.getName());
            return provider.createModel(config);
        });
    }

    /**
     * 获取提供者
     *
     * @param config 模型配置
     * @return 提供者
     */
    public ScoringModelProvider getProvider(ModelEntity config) {
        return registry.getProvider(config);
    }

    /**
     * 检查提供商是否可用
     *
     * @param providerId 提供商 ID
     * @return 是否可用
     */
    public boolean isProviderAvailable(String providerId) {
        return registry.getAvailableProviderIds().contains(providerId.toLowerCase());
    }

    /**
     * 获取所有可用的提供商 ID
     *
     * @return 提供商 ID 列表
     */
    public List<String> getAvailableProviderIds() {
        return registry.getAvailableProviderIds();
    }

    /**
     * 检查是否有可用的重排序模型
     *
     * @return 是否可用
     */
    public boolean hasAvailableProviders() {
        return registry.hasAvailableProviders();
    }

    /**
     * 清除模型缓存
     */
    public void clearCache() {
        modelCache.clear();
        log.info("重排序模型缓存已清除");
    }

    /**
     * 移除指定模型的缓存
     *
     * @param config 模型配置
     */
    public void evictCache(ModelEntity config) {
        String cacheKey = buildCacheKey(config);
        modelCache.remove(cacheKey);
        log.debug("移除重排序模型缓存: {}", cacheKey);
    }

    private String buildCacheKey(ModelEntity config) {
        return String.format("%s:%s:%s", config.getProvider(), config.getName(),
                config.getBaseUrl() != null ? config.getBaseUrl() : "default");
    }
}
