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

import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.dto.result.ChatReference;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 会话消息响应
 *
 * @author Cascade
 * @since 2025/12/20
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "会话消息响应")
public class ConversationMessageResp {

    @Schema(description = "消息ID")
    private Long id;

    @Schema(description = "消息角色：user / assistant")
    private MessageRole role;

    @Schema(description = "用户原始输入内容（未做任何处理）")
    private String userInput;

    @Schema(description = "最终发送给模型的 Prompt（包含系统指令、上下文、模板展开结果）")
    private String finalPrompt;

    @Schema(description = "模型返回的原始输出内容（未裁剪、未渲染）")
    private String modelOutput;

    @Schema(description = "最终对用户展示的内容（安全过滤、格式化后）")
    private String displayContent;

    @Schema(description = "思考链内容")
    private String thinkingContent;

    @Schema(description = "回答引用来源")
    private List<ChatReference> references;

    @Schema(description = "扩展属性")
    private Map<String, Object> variables;

    @Schema(description = "创建时间")
    private Instant createTime;
}
