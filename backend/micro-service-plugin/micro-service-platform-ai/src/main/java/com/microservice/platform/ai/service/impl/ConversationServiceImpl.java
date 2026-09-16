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

package com.microservice.platform.ai.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.dto.req.ConversationPageReq;
import com.microservice.platform.ai.domain.dto.req.ConversationSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ConversationDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationMessageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationPageResp;
import com.microservice.platform.ai.domain.dto.resp.ConversationSaveResp;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import com.microservice.platform.ai.domain.entity.Conversation;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationMapper;
import com.microservice.platform.ai.repository.ConversationMessageMapper;
import com.microservice.platform.ai.repository.ConversationSummaryMapper;
import com.microservice.platform.ai.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * @author xJh
 * @since 2025/10/11
 **/
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl extends SuperServiceImpl<ConversationMapper, Conversation> implements ConversationService {

    private final AuthenticationContext context;
    private final ConversationMessageMapper conversationMessageMapper;
    private final ConversationSummaryMapper conversationSummaryMapper;

    @Override
    public IPage<ConversationPageResp> pageList(ConversationPageReq req) {
        Long userId = context.userId();
        var query = Wraps.<Conversation>lbQ().eq(Conversation::getUserId, userId)
                .like(Conversation::getTitle, req.getTitle()).eq(Conversation::getType, req.getType());
        if (req.getTypes() != null && !req.getTypes().isEmpty()) {
            query.in(Conversation::getType, req.getTypes());
        }
        Page<Conversation> result = this.page(req.buildPage(), query
                .eq(Conversation::getAgentId, req.getAgentId())
                .orderByDesc(Conversation::getPinned).orderByDesc(Conversation::getLastModifyTime).orderByDesc(Conversation::getId));
        return result.convert(this::convertToPageRep);
    }

    @Override
    public ConversationDetailResp detail(Long id) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权访问该会话");
        }
        return convertToDetailResp(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ConversationSaveResp create(ConversationSaveReq req) {
        Long userId = context.userId();
        Conversation conversation = BeanUtilPlus.toBean(req, Conversation.class);
        conversation.setUserId(userId);
        conversation.setMessageCount(0);
        conversation.setPinned(false);
        conversation.setLastModifyTime(Instant.now());
        this.save(conversation);
        return ConversationSaveResp.builder().id(conversation.getId()).build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, ConversationSaveReq req) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权修改该会话");
        }
        // 只允许修改名称
        conversation.setTitle(req.getTitle());
        this.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权删除该会话");
        }
        // 级联删除会话下的消息与摘要，避免孤儿数据残留（且仍可被越权读取）
        conversationMessageMapper.delete(Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, id));
        conversationSummaryMapper.delete(Wraps.<ConversationSummary>lbQ()
                .eq(ConversationSummary::getConversationId, id));
        this.removeById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clearMessages(Long id) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权清空该会话消息");
        }
        // 真正删除消息与摘要，而不仅仅重置计数器（否则"清空"后消息仍可被查询到）
        conversationMessageMapper.delete(Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, id));
        conversationSummaryMapper.delete(Wraps.<ConversationSummary>lbQ()
                .eq(ConversationSummary::getConversationId, id));
        conversation.setMessageCount(0);
        conversation.setLastMessage(null);
        this.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pin(Long id) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权置顶该会话");
        }
        conversation.setPinned(true);
        this.updateById(conversation);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpin(Long id) {
        Long userId = context.userId();
        Conversation conversation = this.getById(id);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权取消置顶该会话");
        }
        conversation.setPinned(false);
        this.updateById(conversation);
    }

    private ConversationPageResp convertToPageRep(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        return BeanUtilPlus.toBean(conversation, ConversationPageResp.class);

    }

    private ConversationDetailResp convertToDetailResp(Conversation conversation) {
        if (conversation == null) {
            return null;
        }
        ConversationDetailResp resp = new ConversationDetailResp();
        BeanUtils.copyProperties(conversation, resp);
        return resp;
    }

    @Override
    public List<ConversationMessageResp> turnList(Long conversationId) {
        Long userId = context.userId();
        Conversation conversation = this.getById(conversationId);
        if (conversation == null) {
            throw CheckedException.notFound("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new CheckedException(403, "无权访问该会话");
        }
        List<ConversationTurn> turnList = conversationMessageMapper.selectList(Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, conversationId).orderByAsc(ConversationTurn::getSequenceNum));
        return turnList.stream().filter(this::isUserVisibleTurn).map(this::convertToMessageResp).toList();
    }

    @Override
    public List<ConversationMessageResp> messageList(Long kbId, Long agentId) {
        Long userId = context.userId();
        var query = Wraps.<Conversation>lbQ().eq(Conversation::getUserId, userId)
                .eq(Conversation::getAgentId, agentId);
        if (kbId != null) {
            // 两端补逗号后精确匹配单个 ID，兼容 MySQL 与 PostgreSQL 的历史逗号分隔存储。
            query.apply("CONCAT(',', knowledge_base_ids, ',') LIKE CONCAT('%,', {0}, ',%')", kbId);
        }
        var conversation = this.baseMapper.selectOne(query.orderByDesc(Conversation::getLastModifyTime)
                .orderByDesc(Conversation::getId).last("LIMIT 1"));
        if (conversation == null) {
            return List.of();
        }
        List<ConversationTurn> turnList = conversationMessageMapper.selectList(Wraps.<ConversationTurn>lbQ()
                .eq(ConversationTurn::getConversationId, conversation.getId()).orderByAsc(ConversationTurn::getSequenceNum));
        return turnList.stream().filter(this::isUserVisibleTurn).map(this::convertToMessageResp).toList();
    }

    /**
     * LangChain4j Tool Loop 消息需要完整持久化以恢复记忆，但不能进入普通聊天展示协议。
     * @param turn turn 参数
     * @return 处理结果
     */
    private boolean isUserVisibleTurn(ConversationTurn turn) {
        if (turn.getRole() == MessageRole.USER) {
            return true;
        }
        if (turn.getRole() != MessageRole.ASSISTANT) {
            return false;
        }
        return turn.getVariables() == null
                || !ConversationMessageServiceImpl.AI_TOOL_REQUEST.equals(
                        turn.getVariables().get(ConversationMessageServiceImpl.MESSAGE_KIND));
    }

    private ConversationMessageResp convertToMessageResp(ConversationTurn turn) {
        ConversationMessageResp resp = BeanUtilPlus.toBean(turn, ConversationMessageResp.class);
        resp.setReferences(ChatReference.fromVariables(turn.getVariables()));
        return resp;
    }
}
