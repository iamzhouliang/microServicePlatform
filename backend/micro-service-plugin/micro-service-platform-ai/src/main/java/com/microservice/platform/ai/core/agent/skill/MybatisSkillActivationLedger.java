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

package com.microservice.platform.ai.core.agent.skill;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.microservice.framework.ai.harness.skill.SkillActivation;
import com.microservice.framework.ai.harness.skill.SkillActivationLedger;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.entity.AiSkillActivation;
import com.microservice.platform.ai.repository.AiSkillActivationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * MyBatis Skill 激活台账。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Component
@RequiredArgsConstructor
public class MybatisSkillActivationLedger implements SkillActivationLedger {

    private final AiSkillActivationMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void record(SkillActivation activation) {
        final Long tenantId = parseId(activation.tenantId(), "租户标识");
        final Long conversationId = parseId(activation.conversationId(), "会话标识");
        final Long userId = parseId(activation.userId(), "用户标识");
        Long count = mapper.selectCount(Wraps.<AiSkillActivation>lbQ()
                .eq(AiSkillActivation::getTenantId, tenantId)
                .eq(AiSkillActivation::getConversationId, conversationId)
                .eq(AiSkillActivation::getTurnId, activation.turnId())
                .eq(AiSkillActivation::getSkillCode, activation.skillCode())
                .eq(AiSkillActivation::getSkillVersion, activation.skillVersion())
                .eq(AiSkillActivation::getContentDigest, activation.contentDigest()));
        if (count != null && count > 0) {
            return;
        }
        AiSkillActivation entity = new AiSkillActivation();
        entity.setId(IdWorker.getId());
        entity.setTenantId(tenantId);
        entity.setUserId(userId);
        entity.setConversationId(conversationId);
        entity.setTurnId(activation.turnId());
        entity.setSkillCode(activation.skillCode());
        entity.setSkillVersion(activation.skillVersion());
        entity.setContentDigest(activation.contentDigest());
        entity.setCreateTime(activation.activatedAt());
        entity.setLastModifyTime(activation.activatedAt());
        entity.setDeleted(false);
        mapper.insert(entity);
    }

    @Override
    public List<SkillActivation> findConversation(String tenantId, String userId, String conversationId) {
        Long tenant = parseId(tenantId, "租户标识");
        Long user = parseId(userId, "用户标识");
        Long conversation = parseId(conversationId, "会话标识");
        return mapper.selectList(Wraps.<AiSkillActivation>lbQ()
                .eq(AiSkillActivation::getTenantId, tenant)
                .eq(AiSkillActivation::getUserId, user)
                .eq(AiSkillActivation::getConversationId, conversation)
                .orderByDesc(AiSkillActivation::getCreateTime)).stream()
                .map(entity -> new SkillActivation(entity.getTenantId().toString(), entity.getUserId().toString(),
                        entity.getConversationId().toString(), entity.getTurnId(), entity.getSkillCode(),
                        entity.getSkillVersion(), entity.getContentDigest(), entity.getCreateTime()))
                .toList();
    }

    private static Long parseId(String value, String field) {
        try {
            long id = Long.parseLong(value);
            if (id <= 0) {
                throw new NumberFormatException();
            }
            return id;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(field + "必须是正整数", failure);
        }
    }
}
