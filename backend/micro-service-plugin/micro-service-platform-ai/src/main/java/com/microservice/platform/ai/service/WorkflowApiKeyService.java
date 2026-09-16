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
import com.microservice.platform.ai.domain.entity.workflow.WorkflowApiKey;

import java.util.List;

/**
 * 工作流 API Key 服务接口
 *
 * @author xJh
 * @since 2026/02/06
 */
public interface WorkflowApiKeyService extends SuperService<WorkflowApiKey> {

    /**
     * 创建 API Key
     *
     * @param workflowId 工作流ID
     * @param name       备注名称
     * @param rateLimit  QPS 限制（null 或 0 表示不限制）
     * @param expireDays 过期天数（null 表示永不过期）
     * @return 创建的 API Key（仅此次返回完整 key，后续查询只返回脱敏值）
     */
    WorkflowApiKey createApiKey(Long workflowId, String name, Integer rateLimit, Integer expireDays);

    /**
     * 查询工作流的 API Key 列表
     *
     * @param workflowId 工作流ID
     * @return API Key 列表
     */
    List<WorkflowApiKey> listByWorkflowId(Long workflowId);

    /**
     * 启用/禁用 API Key
     *
     * @param id     API Key ID
     * @param status 状态: ACTIVE / DISABLED
     */
    void updateStatus(Long id, String status);

    /**
     * 删除 API Key
     *
     * @param id API Key ID
     */
    void deleteApiKey(Long id);

    /**
     * 通过 API Key 认证并返回关联信息
     * 校验 Key 是否存在、是否启用、是否过期，并更新最后使用时间和调用次数
     *
     * @param apiKey API Key 值
     * @return 认证通过返回 ApiKey 实体，否则返回 null
     */
    WorkflowApiKey authenticate(String apiKey);
}
