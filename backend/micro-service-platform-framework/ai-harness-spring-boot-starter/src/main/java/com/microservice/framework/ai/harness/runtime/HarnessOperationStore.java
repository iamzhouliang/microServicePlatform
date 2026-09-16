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

package com.microservice.framework.ai.harness.runtime;

import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import java.time.Instant;
import java.util.Optional;

/**
 * Operation/Saga 持久化端口。生产实现必须使用唯一幂等键、乐观锁和 fencing token。
 *
 * @author xJh
 * @since 2026-07-18
 */
public interface HarnessOperationStore {
    
    HarnessOperation recordIntent(HarnessOperation intent);
    
    Optional<HarnessOperation> find(String tenantId, String operationId);
    
    /**
     * 查询当前会话最近一个待确认、待审批或已审批待恢复的操作。
     *
     * @param tenantId 租户标识
     * @param actorUserId 原操作人标识
     * @param conversationId 会话标识
     * @return 最近的待处理 Operation
     */
    default Optional<HarnessOperation> findPending(String tenantId, String actorUserId, String conversationId) {
        return Optional.empty();
    }
    
    HarnessOperation claimLease(HarnessOperation operation, String leaseOwner, Instant expiresAt);
    
    HarnessOperation save(HarnessOperation operation);
}
