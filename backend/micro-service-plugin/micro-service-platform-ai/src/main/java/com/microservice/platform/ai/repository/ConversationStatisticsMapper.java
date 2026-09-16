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

package com.microservice.platform.ai.repository;

import com.microservice.platform.ai.domain.dto.resp.AiStatisticsRankingResp;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsSummaryResp;
import org.apache.ibatis.annotations.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * 会话统计数据访问
 *
 * @author xiao1
 * @since 2026-06
 */
@Repository
public interface ConversationStatisticsMapper {

    /**
     * 查询统计汇总
     *
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     * @param tenantId 租户标识
     * @return 统计汇总
     */
    AiStatisticsSummaryResp selectUsageSummary(@Param("startTime") Instant startTime,
                                               @Param("endTime") Instant endTime, @Param("tenantId") Long tenantId);

    /**
     * 查询 Token 消耗排行
     *
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     * @param limit     返回条数
     * @param tenantId 租户标识
     * @return Token 消耗排行
     */
    List<AiStatisticsRankingResp> selectTokenRanking(@Param("startTime") Instant startTime,
                                                     @Param("endTime") Instant endTime, @Param("tenantId") Long tenantId, @Param("limit") int limit);

    /**
     * 查询对话次数排行
     *
     * @param startTime 开始时间（含）
     * @param endTime   结束时间（不含）
     * @param limit     返回条数
     * @param tenantId 租户标识
     * @return 对话次数排行
     */
    List<AiStatisticsRankingResp> selectConversationRanking(@Param("startTime") Instant startTime,
                                                            @Param("endTime") Instant endTime, @Param("tenantId") Long tenantId, @Param("limit") int limit);
}
