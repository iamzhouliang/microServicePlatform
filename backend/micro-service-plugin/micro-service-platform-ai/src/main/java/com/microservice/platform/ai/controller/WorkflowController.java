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
import com.microservice.platform.ai.domain.dto.req.WorkflowPageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowDetailResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowNodeDefinitionResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowPageResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowVersionResp;
import com.microservice.platform.ai.service.WorkflowDefinitionService;
import com.microservice.platform.ai.service.WorkflowNodeDefinitionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 工作流管理控制器
 * 提供工作流的 CRUD 操作、版本管理和发布功能
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflows")
@Tag(name = "工作流管理", description = "AI 工作流定义管理")
public class WorkflowController {

    private final WorkflowDefinitionService workflowDefinitionService;
    private final WorkflowNodeDefinitionService workflowNodeDefinitionService;

    @GetMapping("/node-definitions")
    @Operation(summary = "节点定义列表", description = "返回工作流节点的展示、配置、端口和调试契约")
    public List<WorkflowNodeDefinitionResp> nodeDefinitions() {
        return workflowNodeDefinitionService.listNodeDefinitions();
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询工作流列表")
    public IPage<WorkflowPageResp> page(WorkflowPageReq req) {
        return workflowDefinitionService.pageList(req);
    }

    @GetMapping("/{id}")
    @Operation(summary = "工作流详情", description = "获取工作流详细信息")
    public WorkflowDetailResp detail(@Parameter(description = "工作流ID") @PathVariable Long id) {
        return workflowDefinitionService.detail(id);
    }

    @PostMapping
    @Operation(summary = "创建工作流", description = "创建新的工作流，自动创建版本1")
    public Long create(@Validated @RequestBody WorkflowSaveReq req) {
        return workflowDefinitionService.create(req);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新工作流", description = "更新工作流，自动创建新版本")
    public void update(@Parameter(description = "工作流ID") @PathVariable Long id,
                       @Validated @RequestBody WorkflowSaveReq req) {
        workflowDefinitionService.modify(id, req);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工作流", description = "删除指定工作流及其所有版本")
    public void delete(@Parameter(description = "工作流ID") @PathVariable Long id) {
        workflowDefinitionService.delete(id);
    }

    @PostMapping("/{id}/publish")
    @Operation(summary = "发布工作流", description = "将当前版本标记为已发布状态")
    public void publish(@Parameter(description = "工作流ID") @PathVariable Long id) {
        workflowDefinitionService.publish(id);
    }

    @PostMapping("/{id}/archive")
    @Operation(summary = "归档工作流", description = "将工作流标记为已归档状态")
    public void archive(@Parameter(description = "工作流ID") @PathVariable Long id) {
        workflowDefinitionService.archive(id);
    }

    // ==================== 版本管理 API ====================

    @GetMapping("/{id}/versions")
    @Operation(summary = "版本历史", description = "获取工作流的版本历史列表")
    public List<WorkflowVersionResp> getVersionHistory(@Parameter(description = "工作流ID") @PathVariable Long id) {
        return workflowDefinitionService.getVersionHistory(id);
    }

    @GetMapping("/{id}/versions/{version}")
    @Operation(summary = "获取指定版本", description = "获取工作流的指定版本详情")
    public WorkflowVersionResp getVersion(
                                          @Parameter(description = "工作流ID") @PathVariable Long id,
                                          @Parameter(description = "版本号") @PathVariable Integer version) {
        return workflowDefinitionService.getVersion(id, version);
    }

    @PostMapping("/{id}/rollback/{version}")
    @Operation(summary = "回滚版本", description = "将工作流回滚到指定版本")
    public void rollback(
                         @Parameter(description = "工作流ID") @PathVariable Long id,
                         @Parameter(description = "目标版本号") @PathVariable Integer version) {
        workflowDefinitionService.rollback(id, version);
    }

    // ==================== 模板相关 API ====================

    @PostMapping("/from-template/{templateId}")
    @Operation(summary = "从模板创建", description = "从模板创建新的工作流")
    public Long createFromTemplate(
                                   @Parameter(description = "模板ID") @PathVariable Long templateId,
                                   @Parameter(description = "工作流名称") @RequestParam String name,
                                   @Parameter(description = "工作流描述") @RequestParam(required = false) String description) {
        return workflowDefinitionService.createFromTemplate(templateId, name, description);
    }

    @PostMapping("/{id}/copy")
    @Operation(summary = "复制工作流", description = "复制现有工作流")
    public Long copy(
                     @Parameter(description = "工作流ID") @PathVariable Long id,
                     @Parameter(description = "新工作流名称") @RequestParam String name) {
        return workflowDefinitionService.copy(id, name);
    }
}
