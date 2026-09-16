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

package com.microservice.platform.ai.core.assistant.interfaces;

import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.*;
import dev.langchain4j.service.memory.ChatMemoryAccess;

import java.util.List;

/**
 * LangChain4j 对话服务接口，统一承载普通流式聊天、RAG 与多模态调用。
 *
 * @author xJh
 * @since 2025/10/11
 */
public interface ChatAssistant extends ChatMemoryAccess {

    /**
     * 发起普通流式聊天。
     *
     * @param memoryId 会话记忆标识
     * @param userMessage 用户消息
     * @return LangChain4j 流式响应
     */
    TokenStream chatStream(@MemoryId Long memoryId, @UserMessage String userMessage);

    /**
     * 发起平台助手流式聊天，可信身份通过 LangChain4j 官方调用参数传递。
     *
     * @param memoryId 会话记忆标识
     * @param userMessage 用户消息
     * @param invocationParameters Harness 可信调用上下文
     * @return LangChain4j 流式响应
     */
    TokenStream chatStreamWithContext(@MemoryId Long memoryId, @UserMessage String userMessage,
                                      InvocationParameters invocationParameters);

    /**
     * 发起普通 RAG 问答。
     *
     * @param memoryId 会话记忆标识
     * @param message 用户问题
     * @return LangChain4j 流式响应
     */
    TokenStream chatRag(@MemoryId Long memoryId, @UserMessage String message);

    /**
     * 发起带系统消息的多模态聊天。
     *
     * @param memoryId 会话记忆标识
     * @param systemMessage 系统消息
     * @param prompt 用户提示词
     * @param images 用户图片内容
     * @return LangChain4j 流式响应
     */
    @SystemMessage("{{sm}}")
    TokenStream chatWithSystem(@MemoryId Long memoryId, @V("sm") String systemMessage, @UserMessage String prompt, @UserMessage List<ImageContent> images);

    /**
     * 发起不带系统消息的多模态聊天。
     *
     * @param memoryId 会话记忆标识
     * @param prompt 用户提示词
     * @param images 用户图片内容
     * @return LangChain4j 流式响应
     */
    TokenStream chat(@MemoryId Long memoryId, @UserMessage String prompt, @UserMessage List<ImageContent> images);

    /**
     * 清除指定会话记忆。
     *
     * @param memoryId 会话记忆标识
     * @return 是否成功清除
     */
    boolean evictChatMemory(@MemoryId int memoryId);

    /**
     * 获取指定会话记忆。
     *
     * @param memoryId 会话记忆标识
     * @return 当前会话记忆
     */
    ChatMemory getChatMemory(@MemoryId int memoryId);

}
