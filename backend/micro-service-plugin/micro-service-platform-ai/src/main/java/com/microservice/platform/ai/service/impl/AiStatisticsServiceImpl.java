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

import com.microservice.platform.ai.core.constant.AiServiceConstants;
import com.microservice.platform.ai.core.enums.StatisticsPeriod;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsDashboardResp;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsRankingResp;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsSummaryResp;
import com.microservice.platform.ai.repository.ConversationStatisticsMapper;
import com.microservice.platform.ai.service.AiStatisticsService;
import com.microservice.framework.commons.security.AuthenticationContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/**
 * AI 统计服务实现
 *
 * @author xiao1
 * @since 2026-06
 */
@Service
@RequiredArgsConstructor
public class AiStatisticsServiceImpl implements AiStatisticsService {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final AuthenticationContext context;
    private final ConversationStatisticsMapper statisticsMapper;

    private Clock clock = Clock.system(ZoneId.of(AiServiceConstants.DEFAULT_STATISTICS_ZONE));

    @Override
    public AiStatisticsDashboardResp dashboard(StatisticsPeriod period, Integer limit) {
        StatisticsPeriod safePeriod = period == null ? StatisticsPeriod.DAY : period;
        int safeLimit = normalizeLimit(limit);
        ZonedDateTime now = ZonedDateTime.now(clock);
        Instant endTime = now.toInstant();
        Instant startTime = resolveStartTime(safePeriod, now);
        Long tenantId = context.tenantId();

        AiStatisticsSummaryResp summary = normalizeSummary(statisticsMapper.selectUsageSummary(startTime, endTime,
                tenantId));
        List<AiStatisticsRankingResp> tokenRanking = rank(statisticsMapper.selectTokenRanking(startTime, endTime,
                tenantId, safeLimit));
        List<AiStatisticsRankingResp> conversationRanking = rank(statisticsMapper.selectConversationRanking(startTime,
                endTime, tenantId, safeLimit));

        AiStatisticsDashboardResp dashboard = new AiStatisticsDashboardResp();
        dashboard.setPeriod(safePeriod);
        dashboard.setPeriodStart(startTime);
        dashboard.setPeriodEnd(endTime);
        dashboard.setSummary(summary);
        dashboard.setTokenRanking(tokenRanking);
        dashboard.setConversationRanking(conversationRanking);
        return dashboard;
    }

    private Instant resolveStartTime(StatisticsPeriod period, ZonedDateTime now) {
        ZoneId zone = now.getZone();
        LocalDate today = now.toLocalDate();
        LocalDate startDate = switch (period) {
            case DAY -> today;
            case WEEK -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> today.withDayOfMonth(1);
        };
        return startDate.atStartOfDay(zone).toInstant();
    }

    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private AiStatisticsSummaryResp normalizeSummary(AiStatisticsSummaryResp summary) {
        AiStatisticsSummaryResp safeSummary = summary == null ? new AiStatisticsSummaryResp() : summary;
        safeSummary.setInputTokens(defaultZero(safeSummary.getInputTokens()));
        safeSummary.setOutputTokens(defaultZero(safeSummary.getOutputTokens()));
        safeSummary.setConversationCount(defaultZero(safeSummary.getConversationCount()));
        safeSummary.setActiveUserCount(defaultZero(safeSummary.getActiveUserCount()));
        safeSummary.setTotalTokens(safeSummary.getInputTokens() + safeSummary.getOutputTokens());
        return safeSummary;
    }

    private List<AiStatisticsRankingResp> rank(List<AiStatisticsRankingResp> rankings) {
        if (rankings == null || rankings.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < rankings.size(); i++) {
            AiStatisticsRankingResp item = rankings.get(i);
            item.setRank(i + 1);
            item.setInputTokens(defaultZero(item.getInputTokens()));
            item.setOutputTokens(defaultZero(item.getOutputTokens()));
            item.setTotalTokens(item.getInputTokens() + item.getOutputTokens());
            item.setConversationCount(defaultZero(item.getConversationCount()));
        }
        return rankings;
    }

    private Long defaultZero(Long value) {
        return value == null ? 0L : value;
    }
}
