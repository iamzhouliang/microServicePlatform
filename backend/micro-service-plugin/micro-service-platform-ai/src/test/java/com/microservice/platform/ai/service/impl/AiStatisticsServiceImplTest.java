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

import com.microservice.platform.ai.core.enums.StatisticsPeriod;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsDashboardResp;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsRankingResp;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsSummaryResp;
import com.microservice.platform.ai.repository.ConversationStatisticsMapper;
import com.microservice.framework.commons.security.AuthenticationContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI 统计服务")
class AiStatisticsServiceImplTest {

    @Test
    @DisplayName("月统计使用当月零点到当前时间并补齐总 token 和排名")
    void dashboardUsesCurrentMonthRangeAndRanks() {
        ConversationStatisticsMapper mapper = mock(ConversationStatisticsMapper.class);
        AuthenticationContext context = mock(AuthenticationContext.class);
        when(context.tenantId()).thenReturn(100L);
        AiStatisticsServiceImpl service = new AiStatisticsServiceImpl(context, mapper);
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-15T10:30:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectUsageSummary(
                eq(Instant.parse("2026-05-31T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L))).thenReturn(summary(100L, 40L, 12L, 3L));
        when(mapper.selectTokenRanking(
                eq(Instant.parse("2026-05-31T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L),
                eq(10))).thenReturn(List.of(ranking(1L, "小王", 80L, 20L, 9L)));
        when(mapper.selectConversationRanking(
                eq(Instant.parse("2026-05-31T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L),
                eq(10))).thenReturn(List.of(ranking(2L, "小李", 12L, 4L, 11L)));

        AiStatisticsDashboardResp dashboard = service.dashboard(StatisticsPeriod.MONTH, 10);

        assertThat(dashboard.getSummary().getInputTokens()).isEqualTo(100L);
        assertThat(dashboard.getSummary().getOutputTokens()).isEqualTo(40L);
        assertThat(dashboard.getSummary().getTotalTokens()).isEqualTo(140L);
        assertThat(dashboard.getSummary().getConversationCount()).isEqualTo(12L);
        assertThat(dashboard.getTokenRanking()).extracting(AiStatisticsRankingResp::getTotalTokens).containsExactly(100L);
        assertThat(dashboard.getTokenRanking()).extracting(AiStatisticsRankingResp::getRank).containsExactly(1);
        assertThat(dashboard.getConversationRanking()).extracting(AiStatisticsRankingResp::getRank).containsExactly(1);
    }

    @Test
    @DisplayName("日统计从本地日期零点开始并限制排行榜条数")
    void dashboardUsesLocalDayRangeAndClampsLimit() {
        ConversationStatisticsMapper mapper = mock(ConversationStatisticsMapper.class);
        AuthenticationContext context = mock(AuthenticationContext.class);
        when(context.tenantId()).thenReturn(100L);
        AiStatisticsServiceImpl service = new AiStatisticsServiceImpl(context, mapper);
        ReflectionTestUtils.setField(service, "clock",
                Clock.fixed(Instant.parse("2026-06-15T10:30:00Z"), ZoneId.of("Asia/Shanghai")));
        when(mapper.selectUsageSummary(
                eq(Instant.parse("2026-06-14T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L))).thenReturn(summary(null, null, null, null));
        when(mapper.selectTokenRanking(
                eq(Instant.parse("2026-06-14T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L),
                eq(50))).thenReturn(List.of());
        when(mapper.selectConversationRanking(
                eq(Instant.parse("2026-06-14T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L),
                eq(50))).thenReturn(List.of());

        AiStatisticsDashboardResp dashboard = service.dashboard(StatisticsPeriod.DAY, 999);

        assertThat(dashboard.getSummary().getTotalTokens()).isZero();
        assertThat(dashboard.getSummary().getConversationCount()).isZero();
        ArgumentCaptor<Integer> limitCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(mapper).selectTokenRanking(
                eq(Instant.parse("2026-06-14T16:00:00Z")),
                eq(Instant.parse("2026-06-15T10:30:00Z")),
                eq(100L),
                limitCaptor.capture());
        assertThat(limitCaptor.getValue()).isEqualTo(50);
    }

    @Test
    @DisplayName("周统计使用同一个当前时间计算开始和结束时间，避免跨零点窗口倒置")
    void dashboardDerivesWeekRangeFromSingleCurrentTime() {
        ConversationStatisticsMapper mapper = mock(ConversationStatisticsMapper.class);
        AuthenticationContext context = mock(AuthenticationContext.class);
        when(context.tenantId()).thenReturn(100L);
        AiStatisticsServiceImpl service = new AiStatisticsServiceImpl(context, mapper);
        ReflectionTestUtils.setField(service, "clock", new SteppingClock(ZoneId.of("Asia/Shanghai"),
                Instant.parse("2026-06-28T15:59:59Z"), Instant.parse("2026-06-28T16:00:01Z")));
        when(mapper.selectUsageSummary(
                eq(Instant.parse("2026-06-21T16:00:00Z")),
                eq(Instant.parse("2026-06-28T15:59:59Z")),
                eq(100L))).thenReturn(summary(1L, 2L, 3L, 4L));
        when(mapper.selectTokenRanking(
                eq(Instant.parse("2026-06-21T16:00:00Z")),
                eq(Instant.parse("2026-06-28T15:59:59Z")),
                eq(100L),
                eq(10))).thenReturn(List.of());
        when(mapper.selectConversationRanking(
                eq(Instant.parse("2026-06-21T16:00:00Z")),
                eq(Instant.parse("2026-06-28T15:59:59Z")),
                eq(100L),
                eq(10))).thenReturn(List.of());

        AiStatisticsDashboardResp dashboard = service.dashboard(StatisticsPeriod.WEEK, null);

        assertThat(dashboard.getPeriodStart()).isEqualTo(Instant.parse("2026-06-21T16:00:00Z"));
        assertThat(dashboard.getPeriodEnd()).isEqualTo(Instant.parse("2026-06-28T15:59:59Z"));
        assertThat(dashboard.getPeriodStart()).isBefore(dashboard.getPeriodEnd());
    }

    private static final class SteppingClock extends Clock {

        private final ZoneId zone;
        private final Instant[] instants;
        private final AtomicInteger index = new AtomicInteger();

        private SteppingClock(ZoneId zone, Instant... instants) {
            this.zone = zone;
            this.instants = instants;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return new SteppingClock(zone, instants);
        }

        @Override
        public Instant instant() {
            int current = index.getAndUpdate(i -> Math.min(i + 1, instants.length - 1));
            return instants[current];
        }
    }

    private static AiStatisticsSummaryResp summary(Long inputTokens, Long outputTokens, Long conversationCount,
                                                   Long activeUserCount) {
        AiStatisticsSummaryResp summary = new AiStatisticsSummaryResp();
        summary.setInputTokens(inputTokens);
        summary.setOutputTokens(outputTokens);
        summary.setConversationCount(conversationCount);
        summary.setActiveUserCount(activeUserCount);
        return summary;
    }

    private static AiStatisticsRankingResp ranking(Long userId, String userName, Long inputTokens, Long outputTokens,
                                                   Long conversationCount) {
        AiStatisticsRankingResp ranking = new AiStatisticsRankingResp();
        ranking.setUserId(userId);
        ranking.setUserName(userName);
        ranking.setInputTokens(inputTokens);
        ranking.setOutputTokens(outputTokens);
        ranking.setConversationCount(conversationCount);
        return ranking;
    }
}
