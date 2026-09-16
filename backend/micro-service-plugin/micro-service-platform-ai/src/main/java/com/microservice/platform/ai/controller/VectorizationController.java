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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.ai.domain.dto.req.VectorizationTaskPageReq;
import com.microservice.platform.ai.domain.dto.resp.VectorizationResp;
import com.microservice.platform.ai.domain.dto.resp.VectorizationTaskPageResp;
import com.microservice.platform.ai.core.enums.VectorizationTaskStatus;
import com.microservice.platform.ai.service.VectorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 向量化控制器
 * 提供对知识库内容进行向量化的接口
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "向量化管理")
@RequestMapping("/vectorization")
public class VectorizationController {

    private final VectorService vectorService;

    @PostMapping("/page")
    @Operation(summary = "分页查询向量化任务")
    public IPage<VectorizationTaskPageResp> page(@RequestBody VectorizationTaskPageReq params) {
        return vectorService.pageList(params);
    }

    @PostMapping("/knowledge-item/{itemId}")
    @AccessLog(module = "向量化管理", description = "向量化知识条目")
    @Operation(summary = "对知识条目进行向量化")
    public void vectorizeKnowledgeItem(@PathVariable Long itemId) {
        vectorService.vectorizeKnowledgeItem(itemId);
    }

    @PostMapping("/knowledge-items")
    @AccessLog(module = "向量化管理", description = "批量向量化知识条目")
    @Operation(summary = "批量对知识条目进行向量化")
    public String vectorizeKnowledgeItems(@RequestBody List<Long> itemIds) {
        return vectorService.vectorizeKnowledgeItems(itemIds);
    }

    @PostMapping("/document/{docId}")
    @AccessLog(module = "向量化管理", description = "向量化文档")
    @Operation(summary = "对文档进行向量化")
    public String vectorizeDocument(@PathVariable Long docId) {
        return vectorService.vectorizeDocument(docId);
    }

    @PostMapping("/faq/{faqId}")
    @AccessLog(module = "向量化管理", description = "向量化FAQ")
    @Operation(summary = "对FAQ进行向量化")
    public String vectorizeFAQ(@PathVariable Long faqId) {
        return vectorService.vectorizeFAQ(faqId);
    }

    @PostMapping("/structured-data/{dataId}")
    @AccessLog(module = "向量化管理", description = "向量化结构化数据")
    @Operation(summary = "对结构化数据进行向量化")
    public String vectorizeStructuredData(@PathVariable Long dataId) {
        return vectorService.vectorizeStructuredData(dataId);
    }

    @GetMapping("/task/{taskId}")
    @Operation(summary = "查询向量化任务状态")
    public VectorizationTaskStatus getTaskStatus(@PathVariable String taskId) {
        return vectorService.getTaskStatus(taskId);
    }

    @Operation(summary = "查询文档向量化状态")
    @GetMapping("/{itemId}/vectorize-status")
    public VectorizationResp getVectorizeStatus(@PathVariable Long itemId) {

        return vectorService.getVectorizeStatus(itemId);
    }

    @DeleteMapping("/vector/{baseItemId}")
    @AccessLog(module = "向量化管理", description = "删除知识条目向量")
    @Operation(summary = "根据知识条目删除向量")
    public void deleteVector(@PathVariable Long baseItemId) {
        vectorService.deleteVector(baseItemId);
    }

    @DeleteMapping("/vectors")
    @AccessLog(module = "向量化管理", description = "按元数据删除向量")
    @Operation(summary = "按元数据删除向量")
    public void deleteVectorsByMetadata(@RequestParam String key, @RequestParam String value) {
        vectorService.deleteVectorsByMetadata(key, value);
    }
}
