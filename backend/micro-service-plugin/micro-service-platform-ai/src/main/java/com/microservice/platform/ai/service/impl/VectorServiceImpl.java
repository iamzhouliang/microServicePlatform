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

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.concurrent.AsyncExecutor;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.enums.VectorizationTaskStatus;
import com.microservice.platform.ai.core.enums.VectorizationTaskType;
import com.microservice.platform.ai.core.processor.VectorizationProcessor;
import com.microservice.platform.ai.domain.dto.req.VectorizationTaskPageReq;
import com.microservice.platform.ai.domain.dto.resp.VectorizationResp;
import com.microservice.platform.ai.domain.dto.resp.VectorizationTaskPageResp;
import com.microservice.platform.ai.domain.dto.result.VectorizationResult;
import com.microservice.platform.ai.domain.entity.*;
import com.microservice.platform.ai.repository.VectorizationTaskMapper;
import com.microservice.platform.ai.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 向量化服务实现类
 * 主要负责向量化任务的管理和调度，委托实际的向量化处理给VectorizationProcessor
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class VectorServiceImpl extends SuperServiceImpl<VectorizationTaskMapper, VectorizationTask> implements VectorService {

    private final VectorizationProcessor vectorizationProcessor;
    private final VectorizationOrchestrationService vectorizationOrchestrationService;
    private final KnowledgeItemService knowledgeItemService;
    private final VectorMetadataService vectorMetadataService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ModelService modelService;

    @Override
    public void vectorizeKnowledgeItem(Long itemId) {
        KnowledgeItem item = knowledgeItemService.getById(itemId);
        if (item == null) {
            throw CheckedException.notFound("知识条目不存在: " + itemId);
        }
        VectorizationTask vectorizationTask = this.baseMapper.selectOne(Wraps.<VectorizationTask>lbQ()
                .eq(VectorizationTask::getItemId, itemId)
                .orderByDesc(VectorizationTask::getCreateTime)
                .orderByDesc(VectorizationTask::getId)
                .last("LIMIT 1"));
        if (vectorizationTask != null && vectorizationTask.getStatus() != VectorizationTaskStatus.FAILED) {
            throw CheckedException.badRequest("不可再次创建任务");
        }
        item.setStatus(KnowledgeItemStatus.PROCESSING);
        knowledgeItemService.updateById(item);
        String taskId = IdUtil.fastSimpleUUID();
        createTask(taskId, item.getKbId(), itemId, VectorizationTaskType.KNOWLEDGE_ITEM);
        runAsyncTask(taskId, () -> vectorizationOrchestrationService.vectorizeKnowledgeItem(itemId));
    }

    @Override
    public String vectorizeKnowledgeItems(List<Long> itemIds) {
        String taskId = IdUtil.fastSimpleUUID();
        createTask(taskId, null, null, VectorizationTaskType.BATCH);
        // 使用框架 AsyncExecutor（虚拟线程 + TTL 上下文传递），保证异步线程内租户/用户上下文正确，避免落错租户/数据源
        AsyncExecutor.runAsync(() -> {
            int total = itemIds == null ? 0 : itemIds.size();
            List<Long> failedIds = new ArrayList<>();
            try {
                updateTaskStatus(taskId, VectorizationTaskStatus.PROCESSING, 10, null);
                for (Long itemId : itemIds) {
                    try {
                        vectorizationOrchestrationService.vectorizeKnowledgeItem(itemId);
                    } catch (Exception e) {
                        failedIds.add(itemId);
                        log.error("向量化知识条目失败: itemId={}", itemId, e);
                    }
                }
                // 如实反馈成功/部分失败/全部失败，避免误报全部成功
                if (failedIds.isEmpty()) {
                    updateTaskStatus(taskId, VectorizationTaskStatus.COMPLETED, 100, null);
                } else if (failedIds.size() == total) {
                    updateTaskStatus(taskId, VectorizationTaskStatus.FAILED, 0,
                            "全部向量化失败(" + failedIds.size() + "/" + total + ")");
                } else {
                    updateTaskStatus(taskId, VectorizationTaskStatus.COMPLETED, 100,
                            "部分向量化失败(" + failedIds.size() + "/" + total + "): " + failedIds);
                }
            } catch (Exception e) {
                log.error("批量知识条目向量化失败: {}", e.getMessage(), e);
                updateTaskStatus(taskId, VectorizationTaskStatus.FAILED, 0, e.getMessage());
            }
        });
        return taskId;
    }

    @Override
    public String vectorizeDocument(Long docId) {
        String taskId = IdUtil.fastSimpleUUID();
        createTask(taskId, null, null, VectorizationTaskType.DOCUMENT);
        runAsyncTask(taskId, () -> vectorizationOrchestrationService.vectorizeDocument(docId));
        return taskId;
    }

    @Override
    public String vectorizeFAQ(Long faqId) {
        String taskId = IdUtil.fastSimpleUUID();
        createTask(taskId, null, faqId, VectorizationTaskType.FAQ);
        runAsyncTask(taskId, () -> vectorizationOrchestrationService.vectorizeKnowledgeItem(faqId));
        return taskId;
    }

    @Override
    public String vectorizeStructuredData(Long structuredDataId) {
        String taskId = IdUtil.fastSimpleUUID();
        createTask(taskId, null, structuredDataId, VectorizationTaskType.STRUCTURED);
        runAsyncTask(taskId, () -> vectorizationOrchestrationService.vectorizeKnowledgeItem(structuredDataId));
        return taskId;
    }

    @Override
    public VectorizationTaskStatus getTaskStatus(String taskId) {
        VectorizationTask task = baseMapper.selectOne(VectorizationTask::getTaskId, taskId);
        // 未创建任务或状态为空时回退 PENDING，避免返回 null
        return task != null && task.getStatus() != null ? task.getStatus() : VectorizationTaskStatus.PENDING;
    }

    @Override
    public void deleteVector(Long baseItemId) {
        KnowledgeItem item = knowledgeItemService.getById(baseItemId);
        if (item == null) {
            log.warn("删除向量失败，知识条目不存在: itemId={}", baseItemId);
            return;
        }
        List<String> vectorIds = vectorMetadataService.findByItemId(baseItemId).stream()
                .map(VectorMetadata::getVectorId)
                .collect(Collectors.toList());
        if (vectorIds.isEmpty()) {
            log.info("知识条目无向量可删: itemId={}", baseItemId);
            return;
        }
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(item.getKbId());
        ModelEntity modelEntity = modelService.getById(knowledgeBase.getEmbedModelId());
        vectorizationProcessor.batchDeleteVectors(vectorIds, knowledgeBase, modelEntity);
        vectorMetadataService.deleteByItemId(baseItemId);
        // 更新条目标记
        item.setVectorized(false);
        knowledgeItemService.updateById(item);
    }

    @Override
    public void deleteVectorsByMetadata(String metadataKey, String metadataValue) {
        // 将 JSON 元数据过滤下推到 SQL（避免全表 list() 后在内存过滤）；
        // 逻辑删除由 @TableLogic 自动追加 deleted=0，租户范围由多租户插件自动追加，{0}/{1} 为参数占位（防注入）
        List<VectorMetadata> targets = vectorMetadataService.list(Wraps.<VectorMetadata>lbQ()
                .apply("JSON_UNQUOTE(JSON_EXTRACT(metadata, CONCAT('$.\"', {0}, '\"'))) = {1}", metadataKey, metadataValue));
        if (targets.isEmpty()) {
            log.info("按元数据未找到可删除的向量: key={}, value={}", metadataKey, metadataValue);
            return;
        }
        Map<Long, List<String>> kbIdToVectorIds = targets.stream()
                .collect(Collectors.groupingBy(VectorMetadata::getKbId, Collectors.mapping(VectorMetadata::getVectorId, Collectors.toList())));
        int totalDeleted = 0;
        for (Map.Entry<Long, List<String>> entry : kbIdToVectorIds.entrySet()) {
            Long kbId = entry.getKey();
            List<String> vectorIds = entry.getValue();
            if (vectorIds.isEmpty()) {
                continue;
            }
            KnowledgeBase kb = knowledgeBaseService.getById(kbId);
            if (kb == null) {
                continue;
            }
            ModelEntity modelEntity = modelService.getById(kb.getEmbedModelId());
            totalDeleted += vectorizationProcessor.batchDeleteVectors(vectorIds, kb, modelEntity);
        }
        // 软删元数据
        targets.forEach(vm -> vectorMetadataService.deleteByVectorId(vm.getVectorId()));
        log.info("按元数据删除向量完成: key={}, value={}, 删除 {} 条", metadataKey, metadataValue, totalDeleted);
    }

    @Override
    public int deleteVectorsByKbId(Long kbId) {
        List<VectorMetadata> vms = vectorMetadataService.findByKbId(kbId);
        if (vms.isEmpty()) {
            return 0;
        }
        KnowledgeBase kb = knowledgeBaseService.getById(kbId);
        if (kb == null) {
            return 0;
        }
        ModelEntity modelEntity = modelService.getById(kb.getEmbedModelId());
        int deleted = vectorizationProcessor.batchDeleteVectors(
                vms.stream().map(VectorMetadata::getVectorId).collect(Collectors.toList()),
                kb,
                modelEntity);
        vectorMetadataService.deleteByKbId(kbId);
        return deleted;
    }

    @Override
    public int deleteVectorsByItemId(Long itemId) {
        KnowledgeItem item = knowledgeItemService.getById(itemId);
        if (item == null) {
            return 0;
        }
        List<VectorMetadata> vms = vectorMetadataService.findByItemId(itemId);
        if (vms.isEmpty()) {
            return 0;
        }
        KnowledgeBase kb = knowledgeBaseService.getById(item.getKbId());
        ModelEntity modelEntity = modelService.getById(kb.getEmbedModelId());
        final int deleted = vectorizationProcessor.batchDeleteVectors(
                vms.stream().map(VectorMetadata::getVectorId).collect(Collectors.toList()),
                kb,
                modelEntity);
        vectorMetadataService.deleteByItemId(itemId);
        item.setVectorized(false);
        knowledgeItemService.updateById(item);
        return deleted;
    }

    @Override
    public int deleteVectorsByChunkId(Long chunkId) {
        VectorMetadata vm = vectorMetadataService.findByChunkId(chunkId);
        if (vm == null) {
            return 0;
        }
        KnowledgeBase kb = knowledgeBaseService.getById(vm.getKbId());
        ModelEntity modelEntity = modelService.getById(kb.getEmbedModelId());
        int deleted = vectorizationProcessor.batchDeleteVectors(List.of(vm.getVectorId()), kb, modelEntity);
        vectorMetadataService.deleteByVectorId(vm.getVectorId());
        return deleted;
    }

    @Override
    public IPage<VectorizationTaskPageResp> pageList(VectorizationTaskPageReq req) {
        return this.baseMapper.selectPage(req.buildPage(), Wraps.<VectorizationTask>lbQ()
                .orderByDesc(VectorizationTask::getCreateTime))
                .convert(task -> BeanUtilPlus.toBean(task, VectorizationTaskPageResp.class));
    }

    @Override
    public VectorizationResp getVectorizeStatus(Long itemId) {
        VectorizationTask vectorizationTask = this.baseMapper.selectOne(Wraps.<VectorizationTask>lbQ()
                .eq(VectorizationTask::getItemId, itemId)
                .orderByDesc(VectorizationTask::getCreateTime)
                .orderByDesc(VectorizationTask::getId)
                .last("LIMIT 1"));
        if (vectorizationTask != null) {
            return BeanUtilPlus.toBean(vectorizationTask, VectorizationResp.class);
        }
        // 未创建向量化任务时返回明确空态（PENDING/未向量化），避免控制器返回 null body
        VectorizationResp resp = new VectorizationResp();
        resp.setItemId(itemId);
        resp.setStatus(VectorizationTaskStatus.PENDING.getCode());
        resp.setVectorized(false);
        return resp;
    }

    /**
     * 创建向量化任务
     *
     * @param taskId 任务ID
     * @param kbId 知识库ID
     * @param itemId 知识条目ID
     * @param taskType 任务类型
     * @return 任务实体
     */
    private VectorizationTask createTask(String taskId, Long kbId, Long itemId, VectorizationTaskType taskType) {
        VectorizationTask task = VectorizationTask.builder()
                .taskId(taskId)
                .kbId(kbId)
                .itemId(itemId)
                .taskType(taskType)
                .status(VectorizationTaskStatus.PENDING)
                .progress(0)
                .deleted(false)
                .build();

        baseMapper.insert(task);
        return task;
    }

    /**
     * 更新任务状态
     *
     * @param taskId 任务ID
     * @param status 任务状态
     * @param progress 处理进度
     * @param errorMessage 错误信息
     */
    private void updateTaskStatus(String taskId, VectorizationTaskStatus status, Integer progress, String errorMessage) {
        // 按业务 taskId 定点更新状态/进度/错误信息，errorMessage 为 null 时同样置空，语义与原 SQL 一致
        baseMapper.update(null, Wraps.<VectorizationTask>lbU()
                .set(VectorizationTask::getStatus, status)
                .set(VectorizationTask::getProgress, progress)
                .set(VectorizationTask::getErrorMessage, errorMessage)
                .eq(VectorizationTask::getTaskId, taskId));
    }

    /**
     * 统一的异步任务执行模板：负责状态机与异常处理
     * @param taskId taskId 参数
     * @param taskSupplier taskSupplier 参数
     */
    private void runAsyncTask(String taskId, Supplier<VectorizationResult> taskSupplier) {
        // 使用框架 AsyncExecutor（虚拟线程 + TTL 上下文传递），任务体与状态写库全部在同一上下文线程内执行，
        // 保证异步向量化在正确的租户/数据源上下文下读写，且失败可见
        AsyncExecutor.runAsync(() -> {
            try {
                updateTaskStatus(taskId, VectorizationTaskStatus.PROCESSING, 10, null);
                VectorizationResult result = taskSupplier.get();
                updateTaskStatus(taskId, VectorizationTaskStatus.COMPLETED, 100, null);
                if (result != null && result.getTotalTokenUsage() != null) {
                    updateTaskTokenUsage(taskId, result.getTotalTokenUsage());
                }
            } catch (Exception e) {
                log.error("向量化任务执行失败: taskId={}", taskId, e);
                updateTaskStatus(taskId, VectorizationTaskStatus.FAILED, 0, e.getMessage());
            }
        });
    }

    private void updateTaskTokenUsage(String taskId, Integer totalTokenUsage) {
        VectorizationTask task = baseMapper.selectOne(VectorizationTask::getTaskId, taskId);
        if (task != null) {
            task.setTokenUsage(totalTokenUsage);
            baseMapper.updateById(task);
        }
    }
}
