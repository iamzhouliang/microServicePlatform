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

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.dto.req.ConversationPageReq;
import com.microservice.platform.ai.domain.dto.req.ConversationSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ConversationDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationMessageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationPageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationSaveResp;
import com.microservice.platform.ai.domain.entity.Conversation;

import java.util.List;

/**
 * @author xJh
 * @since 2025/10/11
 **/
public interface ConversationService extends SuperService<Conversation> {

    /**
     * 清空会话消息
     *
     * @param id 会话ID
     */
    void clearMessages(Long id);

    /**
     * 置顶会话
     *
     * @param id 会话ID
     */
    void pin(Long id);

    /**
     * 取消置顶会话
     *
     * @param id 会话ID
     */
    void unpin(Long id);

    /**
     * 分页查询会话
     *
     * @param req 查询条件
     * @return 分页结果
     */
    IPage<ConversationPageResp> pageList(ConversationPageReq req);

    /**
     * 获取会话详情
     *
     * @param id 会话ID
     * @return 会话详情
     */
    ConversationDetailResp detail(Long id);

    /**
     * 新增会话
     *
     * @param req 会话信息
     * @return 会话内容
     */
    ConversationSaveResp create(ConversationSaveReq req);

    /**
     * 修改会话
     *
     * @param id  会话ID
     * @param req 会话信息
     */
    void modify(Long id, ConversationSaveReq req);

    /**
     * 删除会话
     *
     * @param id 会话ID
     */
    void remove(Long id);

    /**
     * 获取会话消息列表
     *
     * @param conversationId 会话ID
     * @return 消息列表
     */
    List<ConversationMessageResp> turnList(Long conversationId);

    List<ConversationMessageResp> messageList(Long kbId, Long agentId);

}
