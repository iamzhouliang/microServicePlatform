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

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.microservice.framework.commons.util.DigestUtil;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.ChunkType;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.core.processor.DocumentProcessor;
import com.microservice.platform.ai.domain.dto.req.TextChunkCreateReq;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeChunk;
import com.microservice.platform.ai.repository.KnowledgeChunkMapper;
import com.microservice.platform.ai.service.KnowledgeChunkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识分片服务实现类
 * 负责知识分片的创建和管理，不直接处理向量化
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeChunkServiceImpl extends SuperServiceImpl<KnowledgeChunkMapper, KnowledgeChunk> implements KnowledgeChunkService {

    private final DocumentProcessor documentProcessor;

    @Override
    public List<KnowledgeChunk> listByItemId(Long itemId) {
        return baseMapper.selectList(Wraps.<KnowledgeChunk>lbQ()
                .eq(KnowledgeChunk::getItemId, itemId)
                .orderByAsc(KnowledgeChunk::getCreateTime));
    }

    @Override
    public List<KnowledgeChunk> listByItemIdAndType(Long itemId, ChunkType chunkType) {
        return baseMapper.selectList(Wraps.<KnowledgeChunk>lbQ()
                .eq(KnowledgeChunk::getItemId, itemId)
                .eq(KnowledgeChunk::getChunkType, chunkType)
                .orderByAsc(KnowledgeChunk::getCreateTime));
    }

    @Override
    public List<KnowledgeChunk> listByKbId(Long kbId) {
        return baseMapper.selectList(Wraps.<KnowledgeChunk>lbQ()
                .eq(KnowledgeChunk::getKbId, kbId)
                .orderByAsc(KnowledgeChunk::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createTextChunk(TextChunkCreateReq req) {
        if (StrUtil.isBlank(req.getText())) {
            return null;
        }

        String contentHash = DigestUtil.md5Hex(req.getText());

        KnowledgeChunk chunk = KnowledgeChunk.builder()
                .kbId(req.getKbId())
                .itemId(req.getItemId())
                .chunkType(req.getChunkType())
                .content(req.getText())
                .contentHash(contentHash)
                .metadata(req.getMetadata())
                .deleted(false)
                .build();

        baseMapper.insert(chunk);
        return chunk.getId();
    }

    /**
     * 批量创建文本分片（内部方法）
     *
     * @param kbId         知识库ID
     * @param itemId       知识条目ID
     * @param texts        文本内容列表
     * @param chunkType    分片类型
     * @param metadataList 元数据列表
     * @return 分片ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    private List<Long> batchCreateTextChunks(Long kbId, Long itemId, List<String> texts, ChunkType chunkType, List<Map<String, Object>> metadataList) {
        if (CollUtil.isEmpty(texts)) {
            return Collections.emptyList();
        }

        // 创建知识分片
        List<KnowledgeChunk> chunks = new ArrayList<>();

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            Map<String, Object> metadata = i < metadataList.size() ? metadataList.get(i) : null;

            if (StrUtil.isNotBlank(text)) {
                String contentHash = DigestUtil.md5Hex(text);

                KnowledgeChunk chunk = KnowledgeChunk.builder()
                        .kbId(kbId)
                        .itemId(itemId)
                        .chunkType(chunkType)
                        .content(text)
                        .contentHash(contentHash)
                        .chunkIndex(i)
                        .metadata(metadata)
                        .deleted(false)
                        .build();

                chunks.add(chunk);
            }
        }

        if (!chunks.isEmpty()) {
            baseMapper.insertBatch(chunks);
        }

        return chunks.stream()
                .map(KnowledgeChunk::getId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> createDocumentChunks(KnowledgeBase knowledgeBase, Long itemId, String docId, String content) {
        if (StrUtil.isBlank(content)) {
            return Collections.emptyList();
        }
        List<String> chunks = new ArrayList<>();
        // 分片文档
        if (knowledgeBase.getChunkSize() == null || knowledgeBase.getChunkOverlap() == null) {
            chunks = documentProcessor.splitText(content);
        } else {
            chunks = documentProcessor.splitText(content, knowledgeBase.getChunkSize(), knowledgeBase.getChunkOverlap());
        }

        // 准备元数据
        List<Map<String, Object>> metadataList = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            metadataList.add(Map.of(
                    "docId", docId,
                    "chunkIndex", i,
                    "chunksType", ChunkType.TEXT.getValue(),
                    "ItemType", KnowledgeItemType.DOCUMENT.getValue()));
        }

        // 批量创建分片（强制校验：DOCUMENT 只能生成 TEXT 分片）
        return batchCreateTextChunks(knowledgeBase.getId(), itemId, chunks, ChunkType.TEXT, metadataList);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByItemId(Long itemId) {
        // 逻辑删除交给 @TableLogic：MP 自动转为 UPDATE deleted=1，并自动追加 deleted=0 与租户条件
        baseMapper.delete(KnowledgeChunk::getItemId, itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByKbId(Long kbId) {
        baseMapper.delete(KnowledgeChunk::getKbId, kbId);
    }

    /**
     * 将结构化数据转换为文本
     *
     * @param data 结构化数据
     * @return 文本表示
     */
    private String convertStructuredDataToText(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();

        for (Map.Entry<String, Object> entry : data.entrySet()) {
            sb.append(entry.getKey()).append(": ");

            Object value = entry.getValue();
            if (value instanceof Map) {
                sb.append("\n").append(convertStructuredDataToText((Map<String, Object>) value).indent(2));
            } else if (value instanceof List) {
                List<?> list = (List<?>) value;
                for (Object item : list) {
                    if (item instanceof Map) {
                        sb.append("\n").append(convertStructuredDataToText((Map<String, Object>) item).indent(2));
                    } else {
                        sb.append(item).append(", ");
                    }
                }
                if (!list.isEmpty()) {
                    // 删除最后的逗号和空格
                    sb.setLength(sb.length() - 2);
                }
            } else {
                sb.append(value);
            }

            sb.append("\n");
        }

        return sb.toString();
    }
}
