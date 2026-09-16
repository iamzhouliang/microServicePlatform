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

package com.microservice.platform.ai.domain.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.microservice.platform.ai.domain.dto.result.ChatReference;

import java.util.List;

/**
 * AI助手消息保存请求
 *
 * @author xJh
 * @since 2025/12/28
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "AI助手消息保存请求")
public class AssistantMessageSaveReq {

    @NotNull(message = "会话ID不能为空")
    @Schema(description = "会话ID")
    private Long conversationId;

    @NotNull(message = "用户ID不能为空")
    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "原始内容")
    private String rawContent;

    @Schema(description = "显示内容")
    private String displayContent;

    @Schema(description = "提示词内容")
    private String promptContent;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "模型提供商")
    private String modelProvider;

    @Schema(description = "提示词Token数")
    private Integer promptTokens;

    @Schema(description = "完成Token数")
    private Integer completionTokens;

    @Schema(description = "响应延迟(毫秒)")
    private Long responseLatencyMs;

    @Schema(description = "思考内容")
    private String thinkingContent;

    @Schema(description = "回答引用来源")
    private List<ChatReference> references;

    @Schema(description = "父消息ID")
    private Long parentMessageId;
}
