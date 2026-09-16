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

/**
 * 知识条目通用控制器
 * 统一管理 DOCUMENT / QA_PAIR / STRUCTURED / TEXT_SNIPPET 等类型接口
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/item")
@Tag(name = "知识条目管理")
public class KnowledgeItemController {

    private final KnowledgeItemService knowledgeItemService;

    @PostMapping("/page")
    @Operation(summary = "分页查询知识条目（可按类型过滤）")
    public IPage<KnowledgeItemResp> page(@RequestBody KnowledgeItemPageReq pageReq) {
        return knowledgeItemService.pageList(pageReq);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "查询知识条目详情")
    public KnowledgeItemResp detail(@PathVariable Long id) {
        return knowledgeItemService.detail(id);
    }

    @PostMapping("/create")
    @Operation(summary = "创建知识条目（根据type分发）")
    public Long create(@Valid @RequestBody KnowledgeItemSaveReq req) {
        return knowledgeItemService.create(req);
    }

    @PostMapping("/batch")
    @Operation(summary = "批量创建知识条目")
    public List<Long> batchCreate(@Valid @RequestBody List<KnowledgeItemSaveReq> reqs) {
        return knowledgeItemService.batchCreate(reqs);
    }

    @PutMapping("/{id}/modify")
    @Operation(summary = "更新知识条目")
    public void update(@PathVariable Long id, @Valid @RequestBody KnowledgeItemSaveReq req) {
        knowledgeItemService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除知识条目")
    public void delete(@PathVariable Long id) {
        knowledgeItemService.delete(id);
    }
}
