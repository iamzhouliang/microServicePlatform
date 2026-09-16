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

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.core.enums.ChunkType;
import com.microservice.platform.ai.domain.dto.req.TextChunkCreateReq;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeChunk;

import java.util.List;

/**
 * 知识分片服务接口
 *
 * @author xJh
 * @since 2025/10/20
 **/
public interface KnowledgeChunkService extends SuperService<KnowledgeChunk> {

    /**
     * 根据知识条目ID查询分片
     *
     * @param itemId 知识条目ID
     * @return 分片列表
     */
    List<KnowledgeChunk> listByItemId(Long itemId);

    /**
     * 根据知识条目ID和分片类型查询分片
     *
     * @param itemId 知识条目ID
     * @param chunkType 分片类型
     * @return 分片列表
     */
    List<KnowledgeChunk> listByItemIdAndType(Long itemId, ChunkType chunkType);

    /**
     * 根据知识库ID查询分片
     *
     * @param kbId 知识库ID
     * @return 分片列表
     */
    List<KnowledgeChunk> listByKbId(Long kbId);

    /**
     * 创建文本分片
     *
     * @param req 文本分片创建请求
     * @return 分片ID
     */
    Long createTextChunk(TextChunkCreateReq req);

    /**
     * 为文档创建分片
     *
     * @param knowledgeBase 知识库
     * @param itemId        知识条目ID
     * @param docId         文档ID
     * @param content       文档内容
     * @return 分片ID列表
     */
    List<Long> createDocumentChunks(KnowledgeBase knowledgeBase, Long itemId, String docId, String content);

    /**
     * 根据知识条目ID删除分片
     *
     * @param itemId 知识条目ID
     */
    void deleteByItemId(Long itemId);

    /**
     * 根据知识库ID删除分片
     *
     * @param kbId 知识库ID
     */
    void deleteByKbId(Long kbId);

}
