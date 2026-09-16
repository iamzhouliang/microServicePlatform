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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.dto.req.ChatAgentPageReq;
import com.microservice.platform.ai.domain.dto.req.ChatAgentSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentPageResp;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.domain.entity.Conversation;
import com.microservice.platform.ai.repository.ChatAgentMapper;
import com.microservice.platform.ai.service.ChatAgentService;
import com.microservice.platform.ai.service.ConversationService;
import com.microservice.platform.suite.feign.OssFileFeign;
import com.microservice.platform.suite.feign.domain.resp.OssFileResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.core.env.Environment;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * @author xJh
 * @since 2025/11/4
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatAgentServiceImpl extends SuperServiceImpl<ChatAgentMapper, ChatAgent> implements ChatAgentService {

    private final AuthenticationContext context;
    private final OssFileFeign ossFileFeign;
    private final ConversationService conversationService;
    private final ToolsetResolver toolsetResolver;
    private final Environment environment;

    @Override
    public IPage<ChatAgentPageResp> pageList(ChatAgentPageReq req) {
        return this.baseMapper.selectPage(req.buildPage(), Wraps.<ChatAgent>lbQ().like(ChatAgent::getName, req.getName())
                .eq(ChatAgent::getUserId, req.getUserId()).orderByDesc(ChatAgent::getLastModifyTime))
                .convert(x -> BeanUtil.toBean(x, ChatAgentPageResp.class));
    }

    @Override
    public ChatAgentDetailResp detail(Long id) {
        ChatAgent chatAgent = Optional.ofNullable(this.baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("智能体不存在"));
        return BeanUtil.toBean(chatAgent, ChatAgentDetailResp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ChatAgentSaveReq req) {
        Long userId = context.userId();
        // 检查智能体名称是否重复
        if (existsByName(req.getName(), userId, null)) {
            throw CheckedException.badRequest("智能体名称已存在");
        }
        List<String> toolsetIds = validateToolsets(req.getToolsetIds(), userId);
        ChatAgent chatAgent = BeanUtil.toBean(req, ChatAgent.class);
        chatAgent.setUserId(userId);
        chatAgent.setToolsetIds(toolsetIds);
        this.baseMapper.insert(chatAgent);
        log.info("创建智能体成功，ID: {}, 名称: {}", chatAgent.getId(), chatAgent.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, ChatAgentSaveReq req) {
        ChatAgent existAgent = Optional.ofNullable(this.baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("智能体不存在"));
        Long userId = context.userId();
        // 检查权限：只能修改自己创建的智能体
        if (!Objects.equals(existAgent.getUserId(), userId)) {
            throw CheckedException.forbidden("无权限修改此智能体");
        }
        // 检查智能体名称是否重复（排除当前记录）
        if (existsByName(req.getName(), userId, id)) {
            throw CheckedException.badRequest("智能体名称已存在");
        }
        List<String> toolsetIds = validateToolsets(req.getToolsetIds(), userId);
        ChatAgent chatAgent = BeanUtil.toBean(req, ChatAgent.class);
        chatAgent.setId(id);
        // 保持原用户ID
        chatAgent.setUserId(existAgent.getUserId());
        chatAgent.setToolsetIds(toolsetIds);
        this.baseMapper.updateById(chatAgent);
        log.info("修改智能体成功，ID: {}, 名称: {}", id, chatAgent.getName());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        ChatAgent existAgent = Optional.ofNullable(this.baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("智能体不存在"));
        Long userId = context.userId();
        // 检查权限：只能删除自己创建的智能体
        if (!Objects.equals(existAgent.getUserId(), userId)) {
            throw CheckedException.forbidden("无权限删除此智能体");
        }
        this.baseMapper.deleteById(id);
        log.info("删除智能体成功，ID: {}", id);
    }

    @Override
    public List<ChatAgentPageResp> listByUserId(Long userId) {
        List<ChatAgent> list = this.baseMapper.selectList(Wraps.<ChatAgent>lbQ()
                .eq(ChatAgent::getUserId, userId).orderByDesc(ChatAgent::getLastModifyTime));
        return BeanUtilPlus.toBeans(list, ChatAgentPageResp.class);
    }

    @Override
    public boolean existsByName(String name, Long userId, Long id) {
        if (!StringUtils.hasText(name) || userId == null) {
            return false;
        }
        return this.baseMapper.selectCount(Wraps.<ChatAgent>lbQ().eq(ChatAgent::getName, name)
                .eq(ChatAgent::getUserId, userId).ne(id != null, ChatAgent::getId, id)) > 0;
    }

    @Override
    public String uploadAvatar(MultipartFile file) {
        OssFileResp upload = ossFileFeign.upload(file);
        return upload.getUrl();

    }

    @Override
    public ChatAgentDetailResp detailByAgentId(Long agentId) {
        Conversation one = conversationService.getOne(Wraps.<Conversation>lbQ().eq(Conversation::getId, agentId));
        return BeanUtilPlus.toBean(one, ChatAgentDetailResp.class);
    }

    @Override
    public List<ChatAgentPageResp> listByModelId(String modelId) {
        List<ChatAgent> list = this.baseMapper.selectList(Wraps.<ChatAgent>lbQ()
                .eq(ChatAgent::getModelId, modelId).orderByDesc(ChatAgent::getLastModifyTime));
        return BeanUtilPlus.toBeans(list, ChatAgentPageResp.class);
    }

    private List<String> validateToolsets(List<String> rawIds, Long userId) {
        if (rawIds == null || rawIds.isEmpty()) {
            return List.of();
        }
        List<String> toolsetIds = rawIds.stream()
                .map(value -> value == null ? null : value.trim())
                .toList();
        if (toolsetIds.stream().anyMatch(value -> !StringUtils.hasText(value))
                || new LinkedHashSet<>(toolsetIds).size() != toolsetIds.size()) {
            throw CheckedException.badRequest("Toolset 标识必须非空且不能重复");
        }
        Long tenantId = context.tenantId();
        if (tenantId == null || tenantId <= 0) {
            throw CheckedException.badRequest("认证上下文缺少有效租户信息");
        }
        Set<String> permissions = context.funcPermissionList() == null
                ? Set.of()
                : Set.copyOf(context.funcPermissionList());
        String[] profiles = environment.getActiveProfiles();
        String activeEnvironment = profiles.length == 0 ? "default" : profiles[0];
        ToolsetSelectionContext selection = new ToolsetSelectionContext(tenantId.toString(), userId.toString(),
                "agent-config", "agent-config", "BUSINESS", "CUSTOM_AGENT", activeEnvironment, permissions,
                Set.copyOf(toolsetIds), Set.of());
        Set<String> resolved = toolsetResolver.resolve(selection).selectedToolsets().stream()
                .map(descriptor -> descriptor.id()).collect(java.util.stream.Collectors.toSet());
        if (!resolved.containsAll(toolsetIds)) {
            throw CheckedException.badRequest("Toolset 不存在、当前环境不可用或您没有启用权限");
        }
        return List.copyOf(toolsetIds);
    }
}
