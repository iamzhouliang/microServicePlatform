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
import com.microservice.platform.ai.domain.dto.req.SkillFileUpdateReq;
import com.microservice.platform.ai.domain.dto.req.SkillPageReq;
import com.microservice.platform.ai.domain.dto.req.SkillSaveReq;
import com.microservice.platform.ai.domain.dto.resp.SkillDetailResp;
import com.microservice.platform.ai.domain.dto.resp.SkillPageResp;
import com.microservice.platform.ai.service.SkillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * AI技能控制器
 *
 * @author xJh
 * @since 2026/06/24
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/skills")
@RequiredArgsConstructor
@Tag(name = "AI技能管理", description = "AI技能管理")
public class SkillController {

    private final SkillService skillService;

    @GetMapping("/page")
    @Operation(summary = "分页查询AI技能")
    public IPage<SkillPageResp> pageList(SkillPageReq req) {
        return skillService.pageList(req);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "AI技能详情")
    public SkillDetailResp detail(@PathVariable Long id) {
        return skillService.detail(id);
    }

    @GetMapping("/{id}/preview")
    @Operation(summary = "预览AI技能目录")
    public SkillDetailResp preview(@PathVariable Long id) {
        return skillService.preview(id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "下载AI技能目录")
    public ResponseEntity<byte[]> download(@PathVariable Long id) {
        byte[] bytes = skillService.download(id);
        String filename = URLEncoder.encode("skill-" + id + ".zip", StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + filename)
                .body(bytes);
    }

    @PutMapping("/{id}/files")
    @Operation(summary = "更新AI技能目录内文件")
    public void updateFile(@PathVariable Long id, @Validated @RequestBody SkillFileUpdateReq req) {
        skillService.updateFile(id, req.getPath(), req.getContent());
    }

    @GetMapping("/enabled")
    @Operation(summary = "已发布且启用的AI技能")
    public List<SkillDetailResp> listEnabled() {
        return skillService.listEnabled();
    }

    @PostMapping("/upload")
    @Operation(summary = "上传AI技能目录")
    public void create(@Validated SkillSaveReq req,
                       @RequestPart("files") List<MultipartFile> files,
                       @RequestParam("relativePaths") List<String> relativePaths) {
        skillService.create(req, files, relativePaths);
    }

    @PutMapping("/{id}/upload")
    @Operation(summary = "替换AI技能目录")
    public void modify(@PathVariable Long id,
                       @Validated SkillSaveReq req,
                       @RequestPart("files") List<MultipartFile> files,
                       @RequestParam("relativePaths") List<String> relativePaths) {
        skillService.modify(id, req, files, relativePaths);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除AI技能")
    public void remove(@PathVariable Long id) {
        skillService.remove(id);
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "切换启用状态")
    public void toggleStatus(@PathVariable Long id, @RequestParam Boolean status) {
        skillService.toggleStatus(id, status);
    }

    @PatchMapping("/{id}/published")
    @Operation(summary = "切换发布状态")
    public void togglePublished(@PathVariable Long id, @RequestParam Boolean published) {
        skillService.togglePublished(id, published);
    }
}
