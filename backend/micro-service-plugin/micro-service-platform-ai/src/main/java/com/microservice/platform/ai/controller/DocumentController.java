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

package com.microservice.platform.ai.controller;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.framework.db.mybatisplus.page.PageRequest;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.domain.dto.req.DocumentSaveReq;
import com.microservice.platform.ai.domain.dto.req.DocumentUpdateReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemPageReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeItemResp;
import com.microservice.platform.ai.domain.dto.resp.PreviewChunkResp;
import com.microservice.platform.ai.service.KnowledgeItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 文档控制器
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/rag-documents")
@Tag(name = "文档管理", description = "待改用 /item 通用接口。此控制器作为兼容层保留。")
public class DocumentController {

    private final KnowledgeItemService knowledgeItemService;

    @GetMapping("/{kbId}/page")
    @Operation(summary = "分页查询文档")
    public IPage<KnowledgeItemResp> page(@PathVariable String kbId, PageRequest req) {
        KnowledgeItemPageReq pageReq = new KnowledgeItemPageReq();
        pageReq.setKbId(kbId);
        pageReq.setType(KnowledgeItemType.DOCUMENT.getValue());
        BeanUtil.copyProperties(req, pageReq);
        return knowledgeItemService.pageList(pageReq);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "查询文档详情")
    public KnowledgeItemResp detail(@PathVariable Long id) {
        return knowledgeItemService.detail(id);
    }

    @PostMapping
    @AccessLog(module = "文档管理", description = "创建文档")
    @Operation(summary = "创建文档")
    public void create(@Valid @RequestBody DocumentSaveReq req) {
        knowledgeItemService.createDocument(req);
    }

    // todo 更新文档内容时候，需要删除原始分片以及向量
    @PutMapping("/{id}")
    @AccessLog(module = "文档管理", description = "更新文档")
    @Operation(summary = "更新文档")
    public void update(@PathVariable Long id, @Valid @RequestBody DocumentSaveReq req) {
        DocumentUpdateReq updateReq = DocumentUpdateReq.builder()
                .id(id)
                .title(req.getTitle())
                .content(req.getContent())
                .contentType(req.getContentType())
                .filePath(req.getFilePath())
                .fileSize(req.getFileSize())
                .metadata(req.getMetadata())
                .build();
        knowledgeItemService.updateDocument(updateReq);
    }

    @DeleteMapping("/{id}")
    @AccessLog(module = "文档管理", description = "删除文档")
    @Operation(summary = "删除文档")
    public void delete(@PathVariable Long id) {
        knowledgeItemService.delete(id);
    }

    @PostMapping("/upload")
    @AccessLog(module = "文档管理", description = "上传文档")
    @Operation(summary = "上传文档")
    public Long upload(@RequestParam Long kbId, @RequestParam("file") MultipartFile file) throws IOException {
        return knowledgeItemService.uploadAndProcess(kbId, file);
    }

    @PostMapping("/preview/{itemId}")
    @Operation(summary = "文档预览")
    public String preview(@PathVariable Long itemId) {
        return knowledgeItemService.preview(itemId);
    }

    @PostMapping("/preview-chunk/{itemId}/")
    @Operation(summary = "文档分块预览")
    public List<PreviewChunkResp> previewChunk(@PathVariable Long itemId) {
        return knowledgeItemService.previewChunk(itemId);
    }

    @PostMapping("/bulk-update-chunks")
    @AccessLog(module = "文档管理", description = "批量更新文档分块")
    @Operation(summary = "批量更新文档分块;要求先删除文档的向量化后指向")
    public void bulkUpdateChunks(@RequestBody List<Long> chunkIds) {
        knowledgeItemService.bulkUpdateChunks(chunkIds);
    }

    @PostMapping("/{id}/reprocess")
    @AccessLog(module = "文档管理", description = "重新处理文档")
    @Operation(summary = "重新处理文档")
    public void reprocess(@PathVariable Long id) throws IOException {
        knowledgeItemService.reprocess(id);
    }
}
