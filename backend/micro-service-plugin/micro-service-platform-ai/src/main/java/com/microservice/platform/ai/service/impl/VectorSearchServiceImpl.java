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

import com.microservice.platform.ai.core.provider.embedding.EmbeddingModelService;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.VectorSearchService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 向量搜索服务实现类
 * 专门处理向量搜索相关功能
 *
 * @author xJh
 * @since 2025/10/21
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorSearchServiceImpl implements VectorSearchService {

    private final VectorStoreFactory vectorStoreFactory;
    private final EmbeddingModelService embeddingModelService;

    @Override
    public List<EmbeddingMatch<TextSegment>> search(KnowledgeBase knowledgeBase, ModelEntity modelEntity, String query, int topK) {
        try {
            // 1. 获取向量存储
            EmbeddingStore<TextSegment> embeddingStore = vectorStoreFactory.createForKnowledgeBase(knowledgeBase, modelEntity);

            // 2. 创建嵌入模型实例
            EmbeddingModel embeddingModel = embeddingModelService.getModel(modelEntity);

            // 3. 生成查询向量
            Embedding queryEmbedding = embeddingModel.embed(query).content();

            // 4. 创建搜索请求
            EmbeddingSearchRequest embeddingSearchRequest = EmbeddingSearchRequest.builder()
                    .queryEmbedding(queryEmbedding)
                    .maxResults(topK)
                    .build();

            // 5. 执行向量搜索
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.search(embeddingSearchRequest).matches();

            log.info("向量搜索完成: kbId={}, query={}, topK={}, results={}",
                    knowledgeBase.getId(), query, topK, matches.size());

            return matches;

        } catch (Exception e) {
            log.error("向量搜索失败: kbId={}, query={}", knowledgeBase.getId(), query, e);
            throw new RuntimeException("向量搜索失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isVectorStoreAvailable(KnowledgeBase knowledgeBase, ModelEntity modelEntity) {
        try {
            // 尝试创建向量存储连接
            vectorStoreFactory.createForKnowledgeBase(knowledgeBase, modelEntity);

            // 尝试创建嵌入模型
            embeddingModelService.getModel(modelEntity);

            log.debug("向量存储可用性检查通过: kbId={}, model={}", knowledgeBase.getId(), modelEntity.getName());
            return true;

        } catch (Exception e) {
            log.warn("向量存储不可用: kbId={}, model={}, error={}",
                    knowledgeBase.getId(), modelEntity.getName(), e.getMessage());
            return false;
        }
    }

}
