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

package com.microservice.framework.ai.harness.persistence.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.microservice.framework.commons.entity.SuperEntity;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * 受治理 Tool Operation 的数据库实体。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ai_harness_operation")
public class HarnessOperationEntity extends SuperEntity<Long> {
    
    private String operationId;
    private Long actorUserId;
    private Long conversationId;
    private String initialTurnId;
    private String toolsetId;
    private String toolIdentity;
    private String toolCallId;
    private String argumentsDigest;
    private String idempotencyKey;
    private String status;
    private Boolean confirmationRequired;
    private Boolean approvalRequired;
    private String approvalTarget;
    private String approvalChange;
    private String approvalRisk;
    private Instant approvalExpiresAt;
    private Long fencingToken;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String leaseOwner;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Instant leaseExpiresAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String confirmedTurnId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long approvedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String resultText;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String errorMessage;
    @Version
    private Integer version;
    private Long tenantId;
}
