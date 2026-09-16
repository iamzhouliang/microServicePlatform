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
import com.microservice.platform.ai.domain.dto.req.KnowledgeBasePageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeBaseSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeBaseResp;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 知识库控制器
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "知识库管理")
@RequestMapping("/knowledge-bases")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/page")
    @Operation(summary = "分页查询知识库")
    public IPage<KnowledgeBaseResp> page(@Valid @RequestBody KnowledgeBasePageReq req) {
        return knowledgeBaseService.pageList(req);
    }

    @GetMapping("/list")
    @Operation(summary = "查询知识库列表（不分页）")
    public List<KnowledgeBaseResp> list() {
        return knowledgeBaseService.listAll();
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "查询知识库详情")
    public KnowledgeBaseResp detail(@PathVariable Long id) {
        return knowledgeBaseService.detail(id);
    }

    @PostMapping("/create")
    @AccessLog(module = "知识库", description = "创建知识库")
    @Operation(summary = "创建知识库")
    public void create(@Valid @RequestBody KnowledgeBaseSaveReq req) {
        knowledgeBaseService.create(req);
    }

    @PutMapping("/{id}/modify")
    @AccessLog(module = "知识库", description = "更新知识库")
    @Operation(summary = "更新知识库")
    public void modify(@PathVariable Long id, @Valid @RequestBody KnowledgeBaseSaveReq req) {
        knowledgeBaseService.modify(id, req);
    }

    @DeleteMapping("/{id}")
    @AccessLog(module = "知识库", description = "删除知识库")
    @Operation(summary = "删除知识库")
    public void delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
    }
}
