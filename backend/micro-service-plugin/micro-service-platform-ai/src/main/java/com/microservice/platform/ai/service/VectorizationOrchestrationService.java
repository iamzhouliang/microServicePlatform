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

import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.processor.VectorizationProcessor;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.dto.result.BatchVectorResult;
import com.microservice.platform.ai.domain.dto.result.VectorizationResult;
import com.microservice.platform.ai.domain.entity.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 向量化编排服务
 * 负责协调文档处理和向量化过程
 *
 * @author xJh
 * @since 2025/10/20
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorizationOrchestrationService {

    private final VectorizationProcessor vectorizationProcessor;
    private final KnowledgeChunkService knowledgeChunkService;
    private final KnowledgeItemService knowledgeItemService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelService modelService;
    private final VectorStoreFactory vectorStoreFactory;
    private final VectorMetadataService vectorMetadataService;
    private final GraphExtractionService graphExtractionService;
    private final VectorizationStatusService vectorizationStatusService;

    /**
     * 向量化知识条目
     *
     * @param itemId 知识条目ID
     * @return 向量化任务ID
     * @throws RuntimeException 处理失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public VectorizationResult vectorizeKnowledgeItem(Long itemId) {
        // 获取知识条目
        KnowledgeItem item = knowledgeItemService.getById(itemId);
        if (item == null) {
            throw CheckedException.notFound("知识条目不存在: " + itemId);
        }
        try {
            // 获取知识库和模型配置
            KnowledgeBase kb = knowledgeBaseService.getById(item.getKbId());
            ModelEntity modelEntity = modelService.getById(kb.getEmbedModelId());

            // 图谱处理：如果启用了图谱，则异步提取实体关系并存储到 Neo4j
            if (Boolean.TRUE.equals(kb.getEnableGraph())) {
                graphExtractionService.extractAndStoreAsync(item, kb);
            }

            // 获取相关的知识分片
            List<KnowledgeChunk> chunks = knowledgeChunkService.listByItemId(itemId);
            if (chunks.isEmpty()) {
                throw CheckedException.badRequest("知识条目没有可向量化的分片: itemId=" + itemId);
            }

            // 准备向量化的文本和元数据
            List<String> texts = chunks.stream()
                    .map(KnowledgeChunk::getContent)
                    .collect(Collectors.toList());

            List<Map<String, String>> metadataList = chunks.stream()
                    .map(chunk -> {
                        Map<String, String> metadata = new HashMap<>();
                        metadata.put("kbId", String.valueOf(chunk.getKbId()));
                        metadata.put("itemId", String.valueOf(chunk.getItemId()));
                        metadata.put("chunkId", String.valueOf(chunk.getId()));
                        metadata.put("chunkType", chunk.getChunkType().getValue());
                        metadata.put("kbName", kb.getName());
                        metadata.put("itemTitle", item.getTitle());
                        if (chunk.getMetadata() != null) {
                            chunk.getMetadata()
                                    .forEach((key, value) -> metadata.put(key, value != null ? value.toString() : ""));
                        }
                        return metadata;
                    })
                    .collect(Collectors.toList());

            // 执行批量向量化
            CompletableFuture<BatchVectorResult> future = vectorizationProcessor.batchVectorAndStore(texts,
                    metadataList, kb, modelEntity);
            // 等待完成
            BatchVectorResult batchVectorDTO = future.get();
            List<String> vectorIds = batchVectorDTO.getVectorIds();
            final Integer tokenUsage = batchVectorDTO.getTokenUsage();
            // 保存向量元数据
            List<VectorMetadata> vectorMetadataList = new ArrayList<>();
            for (int i = 0; i < chunks.size() && i < vectorIds.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                final String vectorId = vectorIds.get(i);

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("kbName", kb.getName());
                metadata.put("itemTitle", item.getTitle());
                metadata.put("chunkType", chunk.getChunkType().getValue());
                metadata.put("chunkIndex", chunk.getChunkIndex());
                if (chunk.getMetadata() != null) {
                    metadata.putAll(chunk.getMetadata());
                }

                VectorMetadata vectorMetadata = VectorMetadata.builder()
                        .vectorId(vectorId)
                        .kbId(chunk.getKbId())
                        .itemId(chunk.getItemId())
                        .chunkId(chunk.getId())
                        .chunkType(chunk.getChunkType())
                        .collectionName(generateCollectionName(kb))
                        .textContent(chunk.getContent())
                        .textHash(chunk.getContentHash())
                        .metadata(metadata)
                        .deleted(false)
                        .build();

                vectorMetadataList.add(vectorMetadata);
            }

            // 批量保存向量元数据
            if (!vectorMetadataList.isEmpty()) {
                vectorMetadataService.batchSave(vectorMetadataList);
            }

            // 更新知识分片的向量引用
            for (int i = 0; i < chunks.size() && i < vectorIds.size(); i++) {
                KnowledgeChunk chunk = chunks.get(i);
                String vectorId = vectorIds.get(i);
                chunk.setVectorRef(vectorStoreFactory.vectorReference(vectorId));
                knowledgeChunkService.updateById(chunk);
            }

            // 更新知识条目状态为已向量化
            item.setVectorized(true);
            item.setStatus(KnowledgeItemStatus.PROCESSED);
            knowledgeItemService.updateById(item);

            String taskId = "vectorization_task_" + itemId;
            return new VectorizationResult(taskId, tokenUsage);

        } catch (Exception e) {
            vectorizationStatusService.markFailed(itemId);
            log.error("向量化知识条目失败: itemId={}", itemId, e);
            if (e instanceof CheckedException checkedException) {
                throw checkedException;
            }
            throw new RuntimeException("向量化知识条目失败", e);
        }
    }

    /**
     * 生成集合名称
     * @param kb kb 参数
     * @return 处理结果
     */
    private String generateCollectionName(KnowledgeBase kb) {
        return "kb_" + kb.getId() + "_vectors";
    }

    /**
     * 向量化文档
     *
     * @param docId 文档ID
     * @return 向量化任务ID
     * @throws RuntimeException 处理失败时抛出
     */
    public VectorizationResult vectorizeDocument(Long docId) {
        // 查找关联的知识条目
        KnowledgeItem item = knowledgeItemService.selectBySourceId(docId);
        if (item == null) {
            throw CheckedException.notFound("未找到文档对应的知识条目: docId=" + docId);
        }
        try {
            return vectorizeKnowledgeItem(item.getId());
        } catch (CheckedException e) {
            throw e;
        } catch (Exception e) {
            log.error("向量化文档失败: docId={}", docId, e);
            throw new RuntimeException("向量化文档失败", e);
        }
    }

    /**
     * 向量化FAQ
     *
     * @param faqId FAQ ID
     * @return 向量化任务ID
     */
    public String vectorizeFAQ(Long faqId) {
        return vectorizeKnowledgeItem(faqId).getTaskId();
    }

    /**
     * 向量化结构化数据
     *
     * @param structuredDataId 结构化数据ID
     * @return 向量化任务ID
     */
    public String vectorizeStructuredData(Long structuredDataId) {
        return vectorizeKnowledgeItem(structuredDataId).getTaskId();
    }

    /**
     * 删除知识条目的向量
     *
     * @param itemId 知识条目ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVectorForItem(Long itemId) {
        try {
            // 获取知识条目
            KnowledgeItem item = knowledgeItemService.getById(itemId);
            if (item == null) {
                log.warn("知识条目不存在: itemId={}", itemId);
                return false;
            }

            // 获取知识分片
            List<KnowledgeChunk> chunks = knowledgeChunkService.listByItemId(itemId);

            // 删除向量元数据
            int deletedCount = vectorMetadataService.deleteByItemId(itemId);
            log.info("删除了 {} 个向量元数据记录", deletedCount);

            // 更新知识分片的向量引用
            for (KnowledgeChunk chunk : chunks) {
                chunk.setVectorRef(null);
                knowledgeChunkService.updateById(chunk);
            }

            // 更新知识条目状态为未向量化
            item.setVectorized(false);
            knowledgeItemService.updateById(item);

            return true;
        } catch (Exception e) {
            log.error("删除知识条目向量失败: itemId={}", itemId, e);
            return false;
        }
    }

    /**
     * 删除知识库的所有向量
     *
     * @param kbId 知识库ID
     * @return 是否删除成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteVectorForKnowledgeBase(Long kbId) {
        try {
            // 删除向量元数据
            int deletedCount = vectorMetadataService.deleteByKbId(kbId);
            log.info("删除了知识库 {} 的 {} 个向量元数据记录", kbId, deletedCount);

            // 清除向量存储缓存
            vectorStoreFactory.clearCacheForKnowledgeBase(Long.valueOf(kbId));

            return true;
        } catch (Exception e) {
            log.error("删除知识库向量失败: kbId={}", kbId, e);
            return false;
        }
    }

    /**
     * 重新向量化知识条目
     * 先删除现有向量，再重新创建
     *
     * @param itemId 知识条目ID
     * @return 向量化任务ID
     * @throws RuntimeException 处理失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public String reVectorizeKnowledgeItem(Long itemId) {
        try {
            // 先删除现有向量
            deleteVectorForItem(itemId);
            // 重新向量化
            return vectorizeKnowledgeItem(itemId).getTaskId();
        } catch (CheckedException e) {
            throw e;
        } catch (Exception e) {
            log.error("重新向量化知识条目失败: itemId={}", itemId, e);
            throw new RuntimeException("重新向量化知识条目失败", e);
        }
    }

}
