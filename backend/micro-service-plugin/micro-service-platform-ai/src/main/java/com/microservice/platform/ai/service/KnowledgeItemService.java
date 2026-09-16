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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.domain.dto.req.DocumentSaveReq;
import com.microservice.platform.ai.domain.dto.req.DocumentUpdateReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemPageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeItemResp;
import com.microservice.platform.ai.domain.dto.resp.PreviewChunkResp;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 知识条目服务
 * 管理知识库中的各类知识条目，包括文档、FAQ、结构化数据等
 *
 * @author Levin
 * @since 2025-10
 */
public interface KnowledgeItemService extends SuperService<KnowledgeItem> {

    /**
     * 分页查询知识条目
     *
     * @param req 分页查询请求
     * @return 知识条目分页结果
     */
    IPage<KnowledgeItemResp> pageList(KnowledgeItemPageReq req);

    /**
     * 查询知识条目详情
     *
     * @param id 知识条目ID
     * @return 知识条目详情
     */
    KnowledgeItemResp detail(Long id);

    /**
     * 创建知识条目
     *
     * @param req 知识条目创建请求
     * @return 知识条目ID
     */
    Long create(KnowledgeItemSaveReq req);

    /**
     * 批量创建知识条目
     *
     * @param reqs 知识条目创建请求列表
     * @return 知识条目ID列表
     */
    List<Long> batchCreate(List<KnowledgeItemSaveReq> reqs);

    /**
     * 更新知识条目
     *
     * @param id 知识条目ID
     * @param req 知识条目更新请求
     */
    void update(Long id, KnowledgeItemSaveReq req);

    /**
     * 删除知识条目
     *
     * @param id 知识条目ID
     */
    void delete(Long id);

    /**
     * 根据知识库ID查询知识条目
     *
     * @param kbId 知识库ID
     * @return 知识条目列表
     */
    List<KnowledgeItemResp> listByKbId(Long kbId);

    /**
     * 根据知识库ID和类型查询知识条目
     *
     * @param kbId 知识库ID
     * @param type 条目类型
     * @return 知识条目列表
     */
    List<KnowledgeItemResp> listByKbIdAndType(Long kbId, KnowledgeItemType type);

    /**
     * 统计知识库中各类型知识条目数量
     *
     * @param kbId 知识库ID
     * @return 各类型数量统计
     */
    Map<String, Integer> countByKbIdGroupByType(Long kbId);

    /**
     * 创建FAQ知识条目
     *
     * @param kbId 知识库ID
     * @param question 问题
     * @param answer 答案
     * @param metadata 元数据
     * @return 知识条目ID
     */
    Long createFAQ(Long kbId, String question, String answer, Map<String, Object> metadata);

    /**
     * 创建结构化数据知识条目
     *
     * @param kbId 知识库ID
     * @param title 标题
     * @param structuredData 结构化数据
     * @param metadata 元数据
     * @return 知识条目ID
     */
    Long createStructuredData(Long kbId, String title, Map<String, Object> structuredData, Map<String, Object> metadata);

    /**
     * 创建文档知识条目
     *
     * @param req 文档保存请求
     * @return 知识条目ID
     */
    Long createDocument(DocumentSaveReq req);

    /**
     * 更新文档知识条目
     *
     * @param req 文档更新请求
     */
    void updateDocument(DocumentUpdateReq req);

    Long uploadAndProcess(Long kbId, MultipartFile file) throws IOException;

    void reprocess(Long id) throws IOException;

    KnowledgeItem selectBySourceId(Long docId);

    String preview(Long itemId);

    List<PreviewChunkResp> previewChunk(Long itemId);

    void bulkUpdateChunks(List<Long> chunkIds);
}
