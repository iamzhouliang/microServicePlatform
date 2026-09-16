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

package com.microservice.platform.ai.domain.dto.resp;

import com.microservice.platform.ai.core.enums.StatisticsPeriod;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * AI 统计看板响应
 *
 * @author xiao1
 * @since 2026-06
 */
@Data
@Schema(description = "AI 统计看板响应")
public class AiStatisticsDashboardResp {

    @Schema(description = "统计周期")
    private StatisticsPeriod period;

    @Schema(description = "统计开始时间")
    private Instant periodStart;

    @Schema(description = "统计结束时间")
    private Instant periodEnd;

    @Schema(description = "统计汇总")
    private AiStatisticsSummaryResp summary;

    @Schema(description = "Token 消耗排行")
    private List<AiStatisticsRankingResp> tokenRanking;

    @Schema(description = "对话次数排行")
    private List<AiStatisticsRankingResp> conversationRanking;
}
