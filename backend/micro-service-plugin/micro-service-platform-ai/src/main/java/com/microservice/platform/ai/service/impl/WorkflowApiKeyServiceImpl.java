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

import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.WorkflowApiKeyStatus;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowApiKey;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowDefinition;
import com.microservice.platform.ai.repository.workflow.WorkflowApiKeyMapper;
import com.microservice.platform.ai.repository.workflow.WorkflowDefinitionMapper;
import com.microservice.platform.ai.service.WorkflowApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 工作流 API Key 服务实现
 *
 * @author xJh
 * @since 2026/02/06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowApiKeyServiceImpl extends SuperServiceImpl<WorkflowApiKeyMapper, WorkflowApiKey>
        implements
            WorkflowApiKeyService {

    /**
     * API Key 前缀
     */
    private static final String API_KEY_PREFIX = "sk-wf-";

    private final WorkflowDefinitionMapper workflowDefinitionMapper;
    private final AuthenticationContext context;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WorkflowApiKey createApiKey(Long workflowId, String name, Integer rateLimit, Integer expireDays) {
        // 校验工作流存在且归属当前用户，防止为他人工作流签发 API Key（越权 + 权限提升）
        WorkflowDefinition workflow = requireOwnedWorkflow(workflowId);

        // 生成唯一 API Key
        String apiKey = generateApiKey();

        // 计算过期时间
        Instant expireTime = null;
        if (expireDays != null && expireDays > 0) {
            expireTime = Instant.now().plus(expireDays, ChronoUnit.DAYS);
        }

        WorkflowApiKey entity = WorkflowApiKey.builder()
                .workflowId(workflowId)
                .apiKey(apiKey)
                .name(name)
                .status(WorkflowApiKeyStatus.ACTIVE)
                .rateLimit(rateLimit != null ? rateLimit : 0)
                .expireTime(expireTime)
                .totalCalls(0L)
                .tenantId(workflow.getTenantId())
                .build();

        save(entity);
        log.info("Created API Key for workflow {}: {}", workflowId, maskApiKey(apiKey));
        return entity;
    }

    @Override
    public List<WorkflowApiKey> listByWorkflowId(Long workflowId) {
        // 仅允许查看归属自己的工作流的 API Key
        requireOwnedWorkflow(workflowId);
        return list(Wraps.<WorkflowApiKey>lbQ()
                .eq(WorkflowApiKey::getWorkflowId, workflowId)
                .orderByDesc(WorkflowApiKey::getCreateTime));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, String status) {
        WorkflowApiKeyStatus targetStatus = WorkflowApiKeyStatus.fromCode(status);
        if (targetStatus == null) {
            throw CheckedException.badRequest("无效的状态值，仅支持 ACTIVE 或 DISABLED");
        }
        WorkflowApiKey entity = getById(id);
        if (entity == null) {
            throw CheckedException.notFound("API Key 不存在");
        }
        requireOwnedWorkflow(entity.getWorkflowId());
        entity.setStatus(targetStatus);
        updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteApiKey(Long id) {
        WorkflowApiKey entity = getById(id);
        if (entity == null) {
            throw CheckedException.notFound("API Key 不存在");
        }
        requireOwnedWorkflow(entity.getWorkflowId());
        removeById(id);
        log.info("Deleted API Key: id={}, workflowId={}", id, entity.getWorkflowId());
    }

    /**
     * 校验工作流存在且归属当前用户，防止越权管理他人工作流的 API Key。
     * @param workflowId 工作流标识
     * @return 处理结果
     */
    private WorkflowDefinition requireOwnedWorkflow(Long workflowId) {
        WorkflowDefinition workflow = workflowDefinitionMapper.selectById(workflowId);
        if (workflow == null) {
            throw CheckedException.notFound("工作流不存在");
        }
        if (!Objects.equals(workflow.getUserId(), context.userId())) {
            throw CheckedException.forbidden("无权限操作此工作流的 API Key");
        }
        return workflow;
    }

    @Override
    public WorkflowApiKey authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }

        // 查询 API Key
        WorkflowApiKey entity = getOne(Wraps.<WorkflowApiKey>lbQ()
                .eq(WorkflowApiKey::getApiKey, apiKey));

        if (entity == null) {
            log.debug("API Key not found: {}", maskApiKey(apiKey));
            return null;
        }

        // 检查是否启用
        if (entity.getStatus() != WorkflowApiKeyStatus.ACTIVE) {
            log.debug("API Key is disabled: {}", maskApiKey(apiKey));
            return null;
        }

        // 检查是否过期
        if (entity.getExpireTime() != null && Instant.now().isAfter(entity.getExpireTime())) {
            log.debug("API Key is expired: {}", maskApiKey(apiKey));
            return null;
        }

        // 更新最后使用时间和累计调用次数（单条轻量更新）
        baseMapper.update(null, Wraps.<WorkflowApiKey>lbU()
                .eq(WorkflowApiKey::getId, entity.getId())
                .set(WorkflowApiKey::getLastUsedTime, Instant.now())
                .setSql("total_calls = total_calls + 1"));

        return entity;
    }

    /**
     * 生成 API Key
     * 格式: sk-wf-{uuid}
     * @return 处理结果
     */
    private String generateApiKey() {
        return API_KEY_PREFIX + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 脱敏 API Key（仅显示前 10 位和后 4 位）
     * @param apiKey apiKey 参数
     * @return 处理结果
     */
    public static String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.length() <= 14) {
            return "***";
        }
        return apiKey.substring(0, 10) + "****" + apiKey.substring(apiKey.length() - 4);
    }
}
