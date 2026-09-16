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
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplatePageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplateSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowTemplateResp;
import com.microservice.platform.ai.service.WorkflowTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流模板控制器
 * 提供工作流模板的 CRUD 操作和导入导出功能
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow-templates")
@Tag(name = "工作流模板", description = "工作流模板管理")
public class WorkflowTemplateController {

    private final WorkflowTemplateService workflowTemplateService;

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询工作流模板列表")
    public IPage<WorkflowTemplateResp> page(WorkflowTemplatePageReq req) {
        return workflowTemplateService.pageList(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "模板详情", description = "获取工作流模板详细信息")
    public WorkflowTemplateResp detail(@Parameter(description = "模板ID") @PathVariable Long id) {
        return workflowTemplateService.detail(id);
    }

    @GetMapping("/built-in")
    @Operation(summary = "内置模板", description = "获取所有内置模板列表")
    public List<WorkflowTemplateResp> listBuiltIn() {
        return workflowTemplateService.listBuiltIn();
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类查询", description = "按分类获取模板列表")
    public List<WorkflowTemplateResp> listByCategory(
                                                     @Parameter(description = "模板分类") @PathVariable String category) {
        return workflowTemplateService.listByCategory(category);
    }

    @PostMapping
    @Operation(summary = "创建模板", description = "创建自定义工作流模板")
    public Long create(@Validated @RequestBody WorkflowTemplateSaveReq req) {
        return workflowTemplateService.create(req);
    }

    @PostMapping("/from-workflow/{workflowId}")
    @Operation(summary = "从工作流创建", description = "从现有工作流创建模板")
    public Long createFromWorkflow(
                                   @Parameter(description = "工作流ID") @PathVariable Long workflowId,
                                   @Parameter(description = "模板名称") @RequestParam String name,
                                   @Parameter(description = "模板描述") @RequestParam(required = false) String description,
                                   @Parameter(description = "模板分类") @RequestParam String category) {
        return workflowTemplateService.createFromWorkflow(workflowId, name, description, category);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新模板", description = "更新自定义工作流模板")
    public void update(@Parameter(description = "模板ID") @PathVariable Long id,
                       @Validated @RequestBody WorkflowTemplateSaveReq req) {
        workflowTemplateService.modify(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除模板", description = "删除自定义模板（不能删除内置模板）")
    public void delete(@Parameter(description = "模板ID") @PathVariable Long id) {
        workflowTemplateService.delete(id);
    }

    // ==================== 导入导出 API ====================

    @GetMapping(value = "/{id}/export", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "导出模板", description = "导出模板为 JSON 格式")
    public String exportTemplate(@Parameter(description = "模板ID") @PathVariable Long id) {
        return workflowTemplateService.exportTemplate(id);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "导入模板", description = "从 JSON 导入模板")
    public Long importTemplate(@RequestBody String json) {
        return workflowTemplateService.importTemplate(json);
    }
}
