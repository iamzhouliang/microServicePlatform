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

import com.microservice.framework.security.configuration.client.annotation.IgnoreFeignAuthorize;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 普通对话控制器
 * 提供基础的 AI 对话能力，支持流式输出和会话记忆
 *
  * @return 处理结果
 * @author Levin
 * @since 2025-10
 */
@Validated
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "普通对话", description = "基础 AI 对话接口")
public class ChatController {

    private final ChatService chatService;

    /**
     * 流式对话
     * 支持会话记忆的流式 AI 对话
      * @param req 请求参数
     * @return 处理结果
     */
    @IgnoreFeignAuthorize
    @PostMapping(value = "/stream", produces = "text/event-stream")
    @Operation(summary = "流式对话", description = "支持会话记忆的流式 AI 对话")
    public SseEmitter stream(@Validated @RequestBody AskReq req) {
        return chatService.chatStream(req);
    }
}
