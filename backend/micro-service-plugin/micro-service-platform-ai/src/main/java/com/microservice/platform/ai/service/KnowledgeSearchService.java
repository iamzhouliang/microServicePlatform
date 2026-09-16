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

import com.microservice.platform.ai.domain.dto.resp.EmbeddingMatchResp;

import java.util.List;
import java.util.Map;

/**
 * 知识搜索服务接口
 * 提供语义搜索、关键词搜索和混合搜索功能
 *
 * @author xJh
 * @since 2025/10/21
 **/
public interface KnowledgeSearchService {

    /**
     * 语义搜索
     * 基于向量相似度进行搜索
     *
     * @param kbId 知识库ID
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    List<EmbeddingMatchResp> semanticSearch(Long kbId, String query, int topK);

    /**
     * 关键词搜索
     * 基于文本匹配进行搜索
     *
     * @param kbId 知识库ID
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    List<Map<String, Object>> keywordSearch(Long kbId, String query, int topK);

    /**
     * 混合搜索
     * 结合语义搜索和关键词搜索的结果
     *
     * @param kbId 知识库ID
     * @param query 查询文本
     * @param topK 返回结果数量
     * @return 搜索结果列表
     */
    List<Map<String, Object>> hybridSearch(Long kbId, String query, int topK);

}
