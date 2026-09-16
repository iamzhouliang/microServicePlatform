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

package com.microservice.platform.ai.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.microservice.platform.ai.core.enums.StatisticsPeriod;
import com.microservice.platform.ai.domain.dto.resp.AiStatisticsDashboardResp;
import com.microservice.platform.ai.service.AiStatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 统计控制器
 *
 * @author xiao1
 * @since 2026-06
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/statistics")
@Tag(name = "AI 统计", description = "AI 使用统计接口")
public class AiStatisticsController {

    private final AiStatisticsService statisticsService;

    @GetMapping("/dashboard")
    @SaCheckPermission("ai:statistics")
    @Operation(summary = "AI 统计看板")
    public AiStatisticsDashboardResp dashboard(@RequestParam(defaultValue = "DAY") StatisticsPeriod period,
                                               @RequestParam(defaultValue = "10") Integer limit) {
        return statisticsService.dashboard(period, limit);
    }
}
