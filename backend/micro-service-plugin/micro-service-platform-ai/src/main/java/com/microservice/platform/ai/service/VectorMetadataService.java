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
import com.microservice.platform.ai.domain.entity.VectorMetadata;

import java.util.List;

/**
 * 向量元数据服务接口
 *
 * @author xJh
 * @since 2025/10/20
 */
public interface VectorMetadataService extends SuperService<VectorMetadata> {

    /**
     * 根据知识库ID查找所有向量元数据
     *
     * @param kbId 知识库ID
     * @return 向量元数据列表
     */
    List<VectorMetadata> findByKbId(Long kbId);

    /**
     * 根据知识条目ID查找向量元数据
     *
     * @param itemId 知识条目ID
     * @return 向量元数据列表
     */
    List<VectorMetadata> findByItemId(Long itemId);

    /**
     * 根据知识分片ID查找向量元数据
     *
     * @param chunkId 知识分片ID
     * @return 向量元数据
     */
    VectorMetadata findByChunkId(Long chunkId);

    /**
     * 删除向量元数据
     *
     * @param vectorId 向量ID
     * @return 是否删除成功
     */
    boolean deleteByVectorId(String vectorId);

    /**
     * 根据知识库ID删除所有向量元数据
     *
     * @param kbId 知识库ID
     * @return 删除的数量
     */
    int deleteByKbId(Long kbId);

    /**
     * 根据知识条目ID删除向量元数据
     *
     * @param itemId 知识条目ID
     * @return 删除的数量
     */
    int deleteByItemId(Long itemId);

    /**
     * 批量保存向量元数据
     *
     * @param metadataList 元数据列表
     */
    void batchSave(List<VectorMetadata> metadataList);

}
