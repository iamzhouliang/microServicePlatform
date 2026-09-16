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

package com.microservice.platform.ai.service;

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.dto.req.AssistantMessageSaveReq;
import com.microservice.platform.ai.domain.dto.req.UserMessageSaveReq;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import dev.langchain4j.data.message.ChatMessage;

import java.util.List;

/**
 * 会话消息服务接口
 *
 * @author xiao1
 * @since 2025-10
 */
public interface ConversationMessageService extends SuperService<ConversationTurn> {

    /**
     * 保存用户消息
     *
     * @param req 用户消息保存请求
     * @return 会话轮次
     */
    ConversationTurn saveUserMessage(UserMessageSaveReq req);

    /**
     * 加载当前消息之前的会话上下文.
     *
     * @param conversationId    会话ID
     * @param beforeSequenceNum 当前消息序号，结果不包含该序号及之后的消息
     * @param recentRounds      最近对话轮数
     * @return 按 sequenceNum 正序排列的上下文消息
     */
    List<ConversationTurn> listContextTurns(Long conversationId, Integer beforeSequenceNum, int recentRounds);

    /**
     * 加载序号区间内的会话消息.
     *
     * @param conversationId       会话ID
     * @param afterSequenceNum     起始序号，结果不包含该序号
     * @param beforeSequenceNum    结束序号，结果不包含该序号
     * @param maxMessages          最大消息数
     * @return 按 sequenceNum 正序排列的消息
     */
    List<ConversationTurn> listTurnsBetween(Long conversationId, Integer afterSequenceNum, Integer beforeSequenceNum,
                                            int maxMessages);

    /**
     * 持久化 LangChain4j Tool Loop 中间消息，保留官方 Skills 激活属性。
     * @param conversationId 会话标识
     * @param message 消息内容
     * @param tenantId 租户标识
     * @param userId 用户标识
     */
    void saveToolLoopMessage(Long conversationId, Long userId, Long tenantId, ChatMessage message);

    /**
     * 保存 AI 回复消息。写入失败时抛出异常，由 SSE 层返回失败事件。
     *
     * @param req AI助手消息保存请求
     */
    void saveAssistantMessage(AssistantMessageSaveReq req);
}
