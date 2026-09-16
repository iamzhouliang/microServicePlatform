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

package com.microservice.platform.ai.service;

import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;

import java.util.List;

/**
 * 向量搜索服务接口
 * 专门处理向量搜索相关功能
 *
 * @author xJh
 * @since 2025/10/21
 **/
public interface VectorSearchService {

    /**
     * 执行向量搜索
     *
     * @param knowledgeBase 知识库
     * @param modelEntity 模型配置
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 搜索结果
     */
    List<EmbeddingMatch<TextSegment>> search(KnowledgeBase knowledgeBase, ModelEntity modelEntity, String query, int topK);

    /**
     * 检查向量存储是否可用
     *
     * @param knowledgeBase 知识库
     * @param modelEntity 模型配置
     * @return 是否可用
     */
    boolean isVectorStoreAvailable(KnowledgeBase knowledgeBase, ModelEntity modelEntity);

}
