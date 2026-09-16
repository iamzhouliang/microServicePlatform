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
import com.microservice.platform.ai.core.enums.VectorizationTaskStatus;
import com.microservice.platform.ai.domain.dto.req.VectorizationTaskPageReq;
import com.microservice.platform.ai.domain.dto.resp.VectorizationResp;
import com.microservice.platform.ai.domain.dto.resp.VectorizationTaskPageResp;
import com.microservice.platform.ai.domain.entity.VectorizationTask;

import java.util.List;

/**
 * 向量化服务接口
 * 提供异步向量化处理和检索功能
 *
 * @author xJh
 * @since 2025/10/20
 **/
public interface VectorService extends SuperService<VectorizationTask> {

    /**
     * 对知识条目进行向量化
     *
     * @param itemId 知识条目ID
     */
    void vectorizeKnowledgeItem(Long itemId);

    /**
     * 批量对知识条目进行向量化
     *
     * @param itemIds 知识条目ID列表
     * @return 任务ID
     */
    String vectorizeKnowledgeItems(List<Long> itemIds);

    /**
     * 对文档进行向量化
     *
     * @param docId 文档ID
     * @return 任务ID
     */
    String vectorizeDocument(Long docId);

    /**
     * 对FAQ进行向量化
     *
     * @param faqId FAQ ID
     * @return 任务ID
     */
    String vectorizeFAQ(Long faqId);

    /**
     * 对结构化数据进行向量化
     *
     * @param structuredDataId 结构化数据ID
     * @return 任务ID
     */
    String vectorizeStructuredData(Long structuredDataId);

    /**
     * 获取向量化任务状态
     *
     * @param taskId 任务ID
     * @return 任务状态
     */
    VectorizationTaskStatus getTaskStatus(String taskId);

    /**
     * 删除向量（失败/无数据仅记录日志，走全局响应包装）
     *
     * @param vectorId 向量ID
     */
    void deleteVector(Long vectorId);

    /**
     * 删除指定元数据的所有向量（删除数量记录日志，走全局响应包装）
     *
     * @param metadataKey 元数据键
     * @param metadataValue 元数据值
     */
    void deleteVectorsByMetadata(String metadataKey, String metadataValue);

    /**
     * 按知识库删除全部向量
     * @param kbId kbId 参数
     * @return 处理结果
     */
    int deleteVectorsByKbId(Long kbId);

    /**
     * 按知识条目删除全部向量（等价于 deleteVector）
     * @param itemId itemId 参数
     * @return 处理结果
     */
    int deleteVectorsByItemId(Long itemId);

    /**
     * 按分片删除全部向量
     * @param chunkId chunkId 参数
     * @return 处理结果
     */
    int deleteVectorsByChunkId(Long chunkId);

    /**
     * 分页查询向量化任务列表
     *
     * @param req 查询参数
     * @return 向量化任务列表
     */
    IPage<VectorizationTaskPageResp> pageList(VectorizationTaskPageReq req);

    VectorizationResp getVectorizeStatus(Long itemId);
}
