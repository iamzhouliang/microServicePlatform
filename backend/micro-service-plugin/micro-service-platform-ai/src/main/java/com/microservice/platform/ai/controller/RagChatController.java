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
import com.microservice.platform.ai.core.enums.ConversationType;
import com.microservice.platform.ai.domain.dto.req.AskReq;
import com.microservice.platform.ai.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * RAG对话控制器
 * 提供基于知识库的检索增强生成对话功能
 *
 * @return 处理结果
 * @author xJh
 * @since 2025/10/21
 **/
@Slf4j
@RestController
@RequestMapping("/rag")
@RequiredArgsConstructor
@Tag(name = "RAG对话", description = "基于知识库的检索增强生成对话")
public class RagChatController {

    private final ChatService chatService;

    /**
     * RAG流式对话
     * 基于知识库进行检索增强生成
     * @param askReq askReq 参数
     * @return 处理结果
     */
    @IgnoreFeignAuthorize
    @PostMapping(value = "/chat/stream", produces = "text/event-stream")
    @Operation(summary = "RAG流式对话", description = "基于知识库的检索增强生成对话")
    public SseEmitter ragChatStream(@RequestBody AskReq askReq) {
        askReq.setChatType(ConversationType.KNOWLEDGE_BASE);
        return chatService.chatStream(askReq);
    }
}
