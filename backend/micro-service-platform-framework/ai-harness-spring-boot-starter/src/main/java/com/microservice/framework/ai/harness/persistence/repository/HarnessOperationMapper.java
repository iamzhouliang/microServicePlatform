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

package com.microservice.framework.ai.harness.persistence.repository;

import com.microservice.framework.ai.harness.persistence.entity.HarnessOperationEntity;
import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.stereotype.Repository;

/**
 * Operation 持久化 Mapper，负责带乐观锁和 fencing token 的原子状态更新。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Repository
public interface HarnessOperationMapper extends SuperMapper<HarnessOperationEntity> {
    
    /**
     * 原子获取租约并递增 fencing token，过期执行者无法提交新结果。
     *
     * @param id Operation 主键
     * @param tenantId 租户标识
     * @param version 当前乐观锁版本
     * @param leaseOwner 新租约持有者
     * @param leaseExpiresAt 新租约失效时间
     * @param now 当前时间
     * @param notDeleted 未删除标记，兼容不同数据库的布尔参数
     * @return 成功更新的记录数
     */
    @Update("""
            UPDATE ai_harness_operation
            SET status = 'EXECUTING',
                lease_owner = #{leaseOwner},
                lease_expires_at = #{leaseExpiresAt},
                fencing_token = COALESCE(fencing_token, 0) + 1,
                version = COALESCE(version, 0) + 1,
                last_modify_time = #{now}
            WHERE id = #{id}
              AND tenant_id = #{tenantId}
              AND version = #{version}
              AND status = 'INTENT_RECORDED'
              AND deleted = #{notDeleted}
              AND (lease_owner IS NULL OR lease_owner = '' OR lease_expires_at IS NULL
                   OR lease_expires_at <= #{now} OR lease_owner = #{leaseOwner})
            """)
    int claimLease(@Param("id") Long id, @Param("tenantId") Long tenantId,
                   @Param("version") Integer version, @Param("leaseOwner") String leaseOwner,
                   @Param("leaseExpiresAt") Instant leaseExpiresAt, @Param("now") Instant now,
                   @Param("notDeleted") boolean notDeleted);
    
    /**
     * 将当前会话中过期的执行租约转为结果未知，后续只能通过 Verifier 对账。
     *
     * @param tenantId 租户标识
     * @param actorUserId 原操作人标识
     * @param conversationId 会话标识
     * @param now 当前时间
     * @param reason 转为结果未知时记录的中文原因
     * @param notDeleted 未删除标记，兼容不同数据库的布尔参数
     * @return 实际恢复的 Operation 数量
     */
    @Update("""
            UPDATE ai_harness_operation
            SET status = 'RESULT_UNKNOWN',
                error_message = #{reason},
                lease_owner = NULL,
                lease_expires_at = NULL,
                version = COALESCE(version, 0) + 1,
                last_modify_time = #{now}
            WHERE tenant_id = #{tenantId}
              AND actor_user_id = #{actorUserId}
              AND conversation_id = #{conversationId}
              AND status = 'EXECUTING'
              AND lease_expires_at IS NOT NULL
              AND lease_expires_at <= #{now}
              AND deleted = #{notDeleted}
            """)
    int recoverExpiredExecution(@Param("tenantId") Long tenantId, @Param("actorUserId") Long actorUserId,
                                @Param("conversationId") Long conversationId, @Param("now") Instant now,
                                @Param("reason") String reason, @Param("notDeleted") boolean notDeleted);
}
