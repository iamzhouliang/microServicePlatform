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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.core.processor.DocumentProcessor;
import com.microservice.platform.ai.domain.dto.req.DocumentSaveReq;
import com.microservice.platform.ai.domain.dto.req.DocumentUpdateReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemPageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeItemResp;
import com.microservice.platform.ai.domain.dto.resp.PreviewChunkResp;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeChunk;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import com.microservice.platform.ai.repository.KnowledgeChunkMapper;
import com.microservice.platform.ai.repository.KnowledgeItemMapper;
import com.microservice.platform.ai.service.GraphService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.KnowledgeChunkService;
import com.microservice.platform.ai.service.KnowledgeItemService;
import com.microservice.platform.ai.service.VectorService;
import com.microservice.platform.suite.feign.OssFileFeign;
import com.microservice.platform.suite.feign.domain.resp.OssFileResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识条目服务实现类
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeItemServiceImpl extends SuperServiceImpl<KnowledgeItemMapper, KnowledgeItem> implements KnowledgeItemService {

    private final KnowledgeChunkMapper knowledgeChunkMapper;

    private final KnowledgeChunkService knowledgeChunkService;

    private final DocumentProcessor documentProcessor;

    private final KnowledgeBaseService knowledgeBaseService;

    private final OssFileFeign ossFileFeign;

    // 用 ObjectProvider 延迟解析，避免与 VectorServiceImpl / GraphServiceImpl（均注入知识条目相关服务）形成构造期循环依赖
    private final ObjectProvider<VectorService> vectorServiceProvider;

    private final ObjectProvider<GraphService> graphServiceProvider;

    // 自身代理：uploadAndProcess 将文档落库委托给带 @Transactional 的 createDocument，避免自调用绕过事务代理
    private final ObjectProvider<KnowledgeItemService> selfProvider;

    @Override
    public IPage<KnowledgeItemResp> pageList(KnowledgeItemPageReq req) {
        return baseMapper.selectPage(req.buildPage(),
                Wraps.<KnowledgeItem>lbQ()
                        .eq(req.getKbId() != null, KnowledgeItem::getKbId, req.getKbId())
                        .eq(req.getType() != null, KnowledgeItem::getType, req.getType())
                        .eq(req.getStatus() != null, KnowledgeItem::getStatus, req.getStatus())
                        .orderByDesc(KnowledgeItem::getCreateTime))
                .convert(item -> {
                    KnowledgeItemResp resp = BeanUtil.toBean(item, KnowledgeItemResp.class);
                    resp.setChunkCount(countChunk(item.getId()));
                    return resp;
                });
    }

    @Override
    public KnowledgeItemResp detail(Long id) {
        KnowledgeItem item = baseMapper.selectById(id);
        Optional.ofNullable(item)
                .orElseThrow(() -> CheckedException.notFound("知识条目不存在"));

        KnowledgeItemResp resp = BeanUtil.toBean(item, KnowledgeItemResp.class);
        resp.setChunkCount(countChunk(id));

        return resp;
    }

    /**
     * 统计知识条目的有效分片数量（逻辑删除与租户条件由 MP 自动追加）
     * @param itemId itemId 参数
     * @return 处理结果
     */
    private Integer countChunk(Long itemId) {
        return (int) knowledgeChunkMapper.selectCount(KnowledgeChunk::getItemId, itemId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(KnowledgeItemSaveReq req) {
        KnowledgeItem item = BeanUtil.toBean(req, KnowledgeItem.class);
        item.setVersion(1);
        // 初始状态为待处理
        item.setStatus(KnowledgeItemStatus.PENDING);
        baseMapper.insert(item);
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> batchCreate(List<KnowledgeItemSaveReq> reqs) {
        if (CollUtil.isEmpty(reqs)) {
            return Collections.emptyList();
        }

        List<KnowledgeItem> items = reqs.stream()
                .map(req -> {
                    KnowledgeItem item = BeanUtil.toBean(req, KnowledgeItem.class);
                    item.setVersion(1);
                    item.setStatus(KnowledgeItemStatus.PENDING);
                    return item;
                })
                .collect(Collectors.toList());

        insertBatch(items);

        return items.stream()
                .map(KnowledgeItem::getId)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, KnowledgeItemSaveReq req) {
        KnowledgeItem item = baseMapper.selectById(id);
        Optional.ofNullable(item)
                .orElseThrow(() -> CheckedException.notFound("知识条目不存在"));

        KnowledgeItem updateEntity = BeanUtil.toBean(req, KnowledgeItem.class);
        updateEntity.setId(id);
        // 乐观锁：传入当前版本号 N，MyBatis-Plus 乐观锁插件会生成 WHERE version = N 并自动自增到 N+1。
        // 切勿手工 +1，否则 WHERE version = N+1 与库中 N 不匹配，导致 0 行更新且静默丢失。
        updateEntity.setVersion(item.getVersion());
        if (baseMapper.updateById(updateEntity) == 0) {
            throw CheckedException.badRequest("知识条目已被其他操作修改，请刷新后重试");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        KnowledgeItem item = baseMapper.selectById(id);
        Optional.ofNullable(item)
                .orElseThrow(() -> CheckedException.notFound("知识条目不存在"));

        // 先清理向量与图谱，保证 item ↔ chunk ↔ 向量 ↔ 图谱 一致，避免悬挂数据；外部依赖失败时事务回滚、可见报错
        vectorServiceProvider.getObject().deleteVectorsByItemId(id);
        graphServiceProvider.ifAvailable(graphService -> graphService.deleteGraphForItem(id));

        // 删除知识分片
        knowledgeChunkService.deleteByItemId(id);

        // 删除知识条目
        baseMapper.deleteById(id);
    }

    @Override
    public List<KnowledgeItemResp> listByKbId(Long kbId) {
        List<KnowledgeItem> items = baseMapper.selectList(Wraps.<KnowledgeItem>lbQ()
                .eq(KnowledgeItem::getKbId, kbId)
                .orderByDesc(KnowledgeItem::getCreateTime));
        return items.stream()
                .map(item -> {
                    KnowledgeItemResp resp = BeanUtil.toBean(item, KnowledgeItemResp.class);
                    resp.setChunkCount(countChunk(item.getId()));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<KnowledgeItemResp> listByKbIdAndType(Long kbId, KnowledgeItemType type) {
        List<KnowledgeItem> items = baseMapper.selectList(Wraps.<KnowledgeItem>lbQ()
                .eq(KnowledgeItem::getKbId, kbId)
                .eq(KnowledgeItem::getType, type)
                .orderByDesc(KnowledgeItem::getCreateTime));
        return items.stream()
                .map(item -> {
                    KnowledgeItemResp resp = BeanUtil.toBean(item, KnowledgeItemResp.class);
                    resp.setChunkCount(countChunk(item.getId()));
                    return resp;
                })
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, Integer> countByKbIdGroupByType(Long kbId) {
        // GROUP BY 聚合走 MP selectMaps：逻辑删除(deleted=0)与租户条件仍由框架自动追加，无需手写 XML
        List<Map<String, Object>> rows = baseMapper.selectMaps(Wraps.<KnowledgeItem>q()
                .select("type", "COUNT(*) AS count")
                .eq("kb_id", kbId)
                .groupBy("type"));
        Map<String, Integer> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object type = row.get("type");
            if (type != null) {
                Object count = row.get("count");
                result.put(String.valueOf(type), count == null ? 0 : ((Number) count).intValue());
            }
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFAQ(Long kbId, String question, String answer, Map<String, Object> metadata) {
        // 创建FAQ知识条目（强类型字段）
        KnowledgeItem item = KnowledgeItem.builder()
                .kbId(kbId)
                .type(KnowledgeItemType.QA_PAIR)
                .title(question)
                .question(question)
                .answer(answer)
                .status(KnowledgeItemStatus.PROCESSED)
                .vectorized(false)
                .version(1)
                .metadata(metadata)
                .build();

        baseMapper.insert(item);

        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createStructuredData(Long kbId, String title, Map<String, Object> structuredData, Map<String, Object> metadata) {
        // 创建结构化数据知识条目（结构化属性进metadata，content用于嵌入可为空或拼接）
        // 复制入参 metadata 后再写入，避免污染调用方传入的 Map
        Map<String, Object> meta = metadata == null ? new HashMap<>() : new HashMap<>(metadata);
        meta.put("data", structuredData);
        KnowledgeItem item = KnowledgeItem.builder()
                .kbId(kbId)
                .type(KnowledgeItemType.STRUCTURED)
                .title(title)
                .status(KnowledgeItemStatus.PROCESSED)
                .vectorized(false)
                .version(1)
                .metadata(meta)
                .build();

        baseMapper.insert(item);

        return item.getId();
    }

    @Override
    public KnowledgeItem selectBySourceId(Long docId) {
        return baseMapper.selectById(docId);
    }

    @Override
    public String preview(Long itemId) {
        KnowledgeItem item = baseMapper.selectById(itemId);
        Optional.ofNullable(item).orElseThrow(() -> CheckedException.notFound("知识条目不存在"));
        return item.getContent();
    }

    @Override
    public List<PreviewChunkResp> previewChunk(Long itemId) {
        return knowledgeChunkService.list(Wraps.<KnowledgeChunk>lbQ().eq(KnowledgeChunk::getItemId, itemId)).stream()
                .map(chunk -> PreviewChunkResp.builder()
                        .id(chunk.getId())
                        .content(chunk.getContent())
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public void bulkUpdateChunks(List<Long> chunkIds) {
        // 预期行为：未向量化的分片可批量编辑；已向量化分片需要先撤销向量或触发重新向量化。
        // 该能力尚未实现，显式报错避免静默 no-op 伪装成功（见 AGENTS.md：禁止吞异常/伪装成功）。
        throw new CheckedException("批量更新分块功能尚未实现");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createDocument(DocumentSaveReq req) {
        KnowledgeItem item = KnowledgeItem.builder()
                .kbId(req.getKbId())
                .type(KnowledgeItemType.DOCUMENT)
                .title(req.getTitle())
                .content(req.getContent())
                .contentType(req.getContentType())
                .filePath(req.getFilePath())
                .fileSize(req.getFileSize())
                .status(KnowledgeItemStatus.PENDING)
                .vectorized(false)
                .version(1)
                .metadata(req.getMetadata())
                .build();
        baseMapper.insert(item);
        KnowledgeBase knb = knowledgeBaseService.getById(req.getKbId());
        if (req.getContent() != null && !req.getContent().isEmpty()) {
            knowledgeChunkService.createDocumentChunks(knb, item.getId(), String.valueOf(item.getId()), req.getContent());
        }
        return item.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDocument(DocumentUpdateReq req) {
        KnowledgeItem item = baseMapper.selectById(req.getId());
        Optional.ofNullable(item).orElseThrow(() -> CheckedException.notFound("知识条目不存在"));
        KnowledgeItem update = new KnowledgeItem();
        update.setId(req.getId());
        // 乐观锁：传入当前版本号，交由 MyBatis-Plus 插件自增，避免手工 +1 造成 0 行更新（正文/分片/向量不一致）。
        update.setVersion(item.getVersion());
        update.setTitle(req.getTitle());
        update.setContent(req.getContent());
        update.setContentType(req.getContentType());
        update.setFilePath(req.getFilePath());
        update.setFileSize(req.getFileSize());
        update.setMetadata(req.getMetadata());
        if (baseMapper.updateById(update) == 0) {
            throw CheckedException.badRequest("知识条目已被其他操作修改，请刷新后重试");
        }
        KnowledgeBase kbs = knowledgeBaseService.getById(item.getKbId());
        String content = req.getContent();
        if (content != null && !content.equals(item.getContent())) {
            // 文档正文变化后旧分片已失效，必须重建分片，并清理旧向量、将条目标记为未向量化，等待重新触发向量化
            knowledgeChunkService.deleteByItemId(req.getId());
            knowledgeChunkService.createDocumentChunks(kbs, req.getId(), String.valueOf(req.getId()), content);
            vectorServiceProvider.getObject().deleteVectorsByItemId(req.getId());
        }
    }

    @Override
    public Long uploadAndProcess(Long kbId, MultipartFile file) throws IOException {
        // 外部 I/O（OSS 上传、临时文件、文本抽取）放在事务外，仅文档落库走 createDocument 的事务，收窄事务边界
        OssFileResp uploadResp = ossFileFeign.upload(file);
        DocumentSaveReq req = DocumentSaveReq.builder()
                .filePath(uploadResp.getObjectPath())
                .fileSize(uploadResp.getSize())
                .contentType(uploadResp.getExt())
                .kbId(kbId)
                .title(uploadResp.getOriginalFilename())
                .build();
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        File tempFile = File.createTempFile("upload_", "_" + originalFilename);
        try {
            file.transferTo(tempFile);
            String content = documentProcessor.extractText(tempFile, contentType);
            req.setContent(content);
            // 通过自身代理调用，确保 createDocument 的 @Transactional 生效（Spring 自调用会绕过事务代理）
            return selfProvider.getObject().createDocument(req);
        } finally {
            // 文本抽取完成后清理临时文件，删除失败不影响主流程，仅记录告警
            if (tempFile.exists() && !tempFile.delete()) {
                log.warn("上传临时文件删除失败: {}", tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reprocess(Long id) throws IOException {
        KnowledgeItem item = baseMapper.selectById(id);
        Optional.ofNullable(item).orElseThrow(() -> CheckedException.notFound("知识条目不存在"));
        if (item.getType() != KnowledgeItemType.DOCUMENT) {
            throw new CheckedException("仅支持对文档类型进行重处理");
        }
        knowledgeChunkService.deleteByItemId(id);
        // 重处理会重建分片，旧向量随之失效，清理旧向量并标记未向量化，等待重新触发向量化
        vectorServiceProvider.getObject().deleteVectorsByItemId(id);
        KnowledgeBase knowledgeBase = knowledgeBaseService.getById(item.getKbId());
        if (item.getContent() != null && !item.getContent().isEmpty()) {
            knowledgeChunkService.createDocumentChunks(knowledgeBase, id, String.valueOf(id), item.getContent());
        }
    }
}
