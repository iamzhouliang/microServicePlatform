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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * AI 用户统计排行
 *
 * @author xiao1
 * @since 2026-06
 */
@Data
@Schema(description = "AI 用户统计排行")
public class AiStatisticsRankingResp {

    @Schema(description = "排名")
    private Integer rank;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "用户名称")
    private String userName;

    @Schema(description = "输入 Token 数")
    private Long inputTokens;

    @Schema(description = "输出 Token 数")
    private Long outputTokens;

    @Schema(description = "总 Token 数")
    private Long totalTokens;

    @Schema(description = "对话次数")
    private Long conversationCount;
}
