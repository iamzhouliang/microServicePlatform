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

package com.microservice.platform.ai.domain.entity.workflow;

import com.baomidou.mybatisplus.annotation.TableName;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.platform.ai.core.enums.WorkflowApiKeyStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * 工作流 API Key 实体
 * 用于第三方系统通过 API Key 调用已发布的工作流
 *
 * @author xJh
 * @since 2026/02/06
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_workflow_api_key")
@Schema(name = "WorkflowApiKey", description = "工作流 API Key")
public class WorkflowApiKey extends SuperEntity<Long> {

    @Schema(description = "关联的工作流ID")
    private Long workflowId;

    @Schema(description = "API Key（sk-xxx 格式）")
    private String apiKey;

    @Schema(description = "备注名称")
    private String name;

    @Schema(description = "状态: ACTIVE-启用, DISABLED-禁用")
    private WorkflowApiKeyStatus status;

    @Schema(description = "每秒请求限制（QPS），0 表示不限制")
    private Integer rateLimit;

    @Schema(description = "过期时间，null 表示永不过期")
    private Instant expireTime;

    @Schema(description = "最后使用时间")
    private Instant lastUsedTime;

    @Schema(description = "累计调用次数")
    private Long totalCalls;

    @Schema(description = "所属租户ID")
    private Long tenantId;
}
