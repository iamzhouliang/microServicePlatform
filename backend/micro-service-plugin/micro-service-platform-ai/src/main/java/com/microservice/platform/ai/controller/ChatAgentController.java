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
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.platform.ai.domain.dto.req.ChatAgentPageReq;
import com.microservice.platform.ai.domain.dto.req.ChatAgentSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentPageResp;
import com.microservice.platform.ai.service.ChatAgentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 智能体管理控制器
 * 管理 AI 智能体，支持自定义提示词、知识库关联、工具调用等
 *
 * @author Levin
 * @since 2025-11
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/chat-agents")
@Tag(name = "智能体管理", description = "AI 智能体配置管理")
public class ChatAgentController {

    private final ChatAgentService chatAgentService;
    private final AuthenticationContext authenticationContext;

    @PostMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询智能体列表")
    public IPage<ChatAgentPageResp> page(@RequestBody ChatAgentPageReq req) {
        return chatAgentService.pageList(req);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "智能体详情", description = "获取智能体详细配置")
    public ChatAgentDetailResp detail(@Parameter(description = "智能体 ID") @PathVariable Long id) {
        return chatAgentService.detail(id);
    }

    @GetMapping("/mine")
    @Operation(summary = "我的智能体", description = "获取当前用户创建的智能体列表")
    public List<ChatAgentPageResp> mine() {
        Long userId = authenticationContext.userId();
        return chatAgentService.listByUserId(userId);
    }

    @GetMapping("/by-model/{modelId}")
    @Operation(summary = "按模型查询", description = "根据模型 ID 获取智能体列表")
    public List<ChatAgentPageResp> listByModel(@Parameter(description = "模型 ID") @PathVariable String modelId) {
        return chatAgentService.listByModelId(modelId);
    }

    @GetMapping("/exists")
    @Operation(summary = "名称检查", description = "检查智能体名称是否已存在")
    public boolean checkNameExists(
                                   @Parameter(description = "智能体名称") @RequestParam String name,
                                   @Parameter(description = "排除的 ID（编辑时使用）") @RequestParam(required = false) Long excludeId) {
        Long userId = authenticationContext.userId();
        return chatAgentService.existsByName(name, userId, excludeId);
    }

    @PostMapping("/create")
    @AccessLog(module = "AI智能体", description = "新增智能体")
    @Operation(summary = "新增智能体", description = "创建新的 AI 智能体")
    public void create(@Validated @RequestBody ChatAgentSaveReq req) {
        chatAgentService.create(req);
    }

    @PutMapping("/{id}/modify")
    @AccessLog(module = "AI智能体", description = "修改智能体")
    @Operation(summary = "修改智能体", description = "修改智能体配置")
    public void update(@Parameter(description = "智能体 ID") @PathVariable Long id,
                       @Validated @RequestBody ChatAgentSaveReq req) {
        chatAgentService.modify(id, req);
    }

    @DeleteMapping("/{id}")
    @AccessLog(module = "AI智能体", description = "删除智能体")
    @Operation(summary = "删除智能体", description = "删除指定智能体")
    public void delete(@Parameter(description = "智能体 ID") @PathVariable Long id) {
        chatAgentService.remove(id);
    }

    @PostMapping("/avatar")
    @Operation(summary = "上传头像", description = "上传智能体头像，返回 URL")
    public String uploadAvatar(@RequestParam("file") MultipartFile file) {
        return chatAgentService.uploadAvatar(file);
    }
}
