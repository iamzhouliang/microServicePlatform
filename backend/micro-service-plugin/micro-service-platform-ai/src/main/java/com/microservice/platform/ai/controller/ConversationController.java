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
import com.microservice.platform.ai.domain.dto.req.ConversationPageReq;
import com.microservice.platform.ai.domain.dto.req.ConversationSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ConversationDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationMessageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationPageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationSaveResp;
import com.microservice.platform.ai.service.ConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * @author xJh
 * @since 2025/10/30
 **/
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/conversations")
@Tag(name = "会话管理", description = "会话管理接口")
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping("/page")
    @Operation(summary = "分页查询会话")
    public IPage<ConversationPageResp> pageList(@RequestBody ConversationPageReq req) {
        return conversationService.pageList(req);
    }

    @Operation(summary = "获取会话详情")
    @GetMapping("/{id}/detail")
    public ConversationDetailResp detail(@PathVariable Long id) {
        return conversationService.detail(id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "获取普通会话消息列表")
    public List<ConversationMessageResp> turnList(@PathVariable Long id) {
        return conversationService.turnList(id);
    }

    @GetMapping("/messages")
    @Operation(summary = "获取知识库会话消息列表")
    public List<ConversationMessageResp> messageList(Long kbId, Long agentId) {
        return conversationService.messageList(kbId, agentId);
    }

    @PostMapping("/create")
    @AccessLog(module = "AI会话", description = "新增会话")
    @Operation(summary = "新增会话")
    public ConversationSaveResp create(@Validated @RequestBody ConversationSaveReq req) {
        return conversationService.create(req);
    }

    @AccessLog(module = "AI会话", description = "修改会话")
    @Operation(summary = "修改会话")
    @PutMapping("/{id}/modify")
    public void modify(@PathVariable Long id, @Validated @RequestBody ConversationSaveReq req) {
        conversationService.modify(id, req);
    }

    @AccessLog(module = "AI会话", description = "删除会话")
    @Operation(summary = "删除会话")
    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        conversationService.remove(id);
    }

    @AccessLog(module = "AI会话", description = "清空会话消息")
    @Operation(summary = "清空会话消息")
    @DeleteMapping("/{id}/messages")
    public void clearMessages(@PathVariable Long id) {
        conversationService.clearMessages(id);
    }

    @Operation(summary = "置顶会话")
    @PutMapping("/{id}/pin")
    public void pin(@PathVariable Long id) {
        conversationService.pin(id);
    }

    @Operation(summary = "取消置顶会话")
    @PutMapping("/{id}/unpin")
    public void unpin(@PathVariable Long id) {
        conversationService.unpin(id);
    }

}
