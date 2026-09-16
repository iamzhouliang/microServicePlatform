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
import com.microservice.framework.db.mybatisplus.page.PageRequest;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.domain.dto.req.FAQSaveReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemPageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeItemSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeItemResp;
import com.microservice.platform.ai.service.KnowledgeItemService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * FAQ控制器
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/faq")
@Tag(name = "FAQ管理", description = "建议改用 /item 通用接口。此控制器作为兼容层保留。")
public class FAQController {

    private final KnowledgeItemService knowledgeItemService;

    @GetMapping("/list")
    @Operation(summary = "查询FAQ列表")
    public List<KnowledgeItemResp> list(@RequestParam Long kbId) {
        return knowledgeItemService.listByKbIdAndType(kbId, KnowledgeItemType.QA_PAIR);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询FAQ")
    public IPage<KnowledgeItemResp> page(@RequestParam String kbId, PageRequest req) {
        KnowledgeItemPageReq pageReq = new KnowledgeItemPageReq();
        pageReq.setKbId(kbId);
        pageReq.setType(KnowledgeItemType.QA_PAIR.getValue());
        BeanUtil.copyProperties(req, pageReq);
        return knowledgeItemService.pageList(pageReq);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "查询FAQ详情")
    public KnowledgeItemResp detail(@PathVariable Long id) {
        return knowledgeItemService.detail(id);
    }

    @PostMapping
    @Operation(summary = "创建FAQ")
    public void create(@Valid @RequestBody FAQSaveReq req) {
        knowledgeItemService.createFAQ(
                req.getKbId(),
                req.getQuestion(),
                req.getAnswer(),
                req.getMetadata());
    }

    @PostMapping("/batch")
    @Operation(summary = "批量创建FAQ")
    public List<Long> batchCreate(@Valid @RequestBody List<FAQSaveReq> reqs) {
        List<KnowledgeItemSaveReq> itemReqs = reqs.stream()
                .map(req -> {
                    KnowledgeItemSaveReq itemReq = new KnowledgeItemSaveReq();
                    itemReq.setKbId(req.getKbId());
                    itemReq.setType(KnowledgeItemType.QA_PAIR.getValue());
                    itemReq.setContent(Map.of(
                            "question", req.getQuestion(),
                            "answer", req.getAnswer()));
                    itemReq.setMetadata(req.getMetadata());
                    return itemReq;
                })
                .collect(Collectors.toList());

        return knowledgeItemService.batchCreate(itemReqs);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新FAQ")
    public void update(@PathVariable Long id, @Valid @RequestBody FAQSaveReq req) {
        KnowledgeItemSaveReq itemReq = new KnowledgeItemSaveReq();
        itemReq.setKbId(req.getKbId());
        itemReq.setType(KnowledgeItemType.QA_PAIR.getValue());
        itemReq.setContent(Map.of(
                "question", req.getQuestion(),
                "answer", req.getAnswer()));
        itemReq.setMetadata(req.getMetadata());

        knowledgeItemService.update(id, itemReq);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除FAQ")
    public void delete(@PathVariable Long id) {
        knowledgeItemService.delete(id);
    }
}
