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

import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.entity.VectorMetadata;
import com.microservice.platform.ai.repository.VectorMetadataMapper;
import com.microservice.platform.ai.service.VectorMetadataService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 向量元数据服务实现类
 *
 * @author xJh
 * @since 2025/10/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorMetadataServiceImpl extends SuperServiceImpl<VectorMetadataMapper, VectorMetadata> implements VectorMetadataService {

    @Override
    public List<VectorMetadata> findByKbId(Long kbId) {
        return baseMapper.selectList(Wraps.<VectorMetadata>lbQ()
                .eq(VectorMetadata::getKbId, kbId)
                .eq(VectorMetadata::getDeleted, false));
    }

    @Override
    public List<VectorMetadata> findByItemId(Long itemId) {
        return baseMapper.selectList(Wraps.<VectorMetadata>lbQ()
                .eq(VectorMetadata::getItemId, itemId)
                .eq(VectorMetadata::getDeleted, false));
    }

    @Override
    public VectorMetadata findByChunkId(Long chunkId) {
        return baseMapper.selectOne(Wraps.<VectorMetadata>lbQ()
                .eq(VectorMetadata::getChunkId, chunkId)
                .eq(VectorMetadata::getDeleted, false));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByVectorId(String vectorId) {
        return baseMapper.update(null, Wraps.<VectorMetadata>lbU()
                .eq(VectorMetadata::getVectorId, vectorId)
                .set(VectorMetadata::getDeleted, true)) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByKbId(Long kbId) {
        return baseMapper.update(null, Wraps.<VectorMetadata>lbU()
                .eq(VectorMetadata::getKbId, kbId)
                .set(VectorMetadata::getDeleted, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteByItemId(Long itemId) {
        return baseMapper.update(null, Wraps.<VectorMetadata>lbU()
                .eq(VectorMetadata::getItemId, itemId)
                .set(VectorMetadata::getDeleted, true));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchSave(List<VectorMetadata> metadataList) {
        if (metadataList == null || metadataList.isEmpty()) {
            return;
        }

        // 设置默认值
        metadataList.forEach(metadata -> {
            if (metadata.getDeleted() == null) {
                metadata.setDeleted(false);
            }
        });

        baseMapper.insertBatch(metadataList);
    }

}
