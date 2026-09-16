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

package com.microservice.platform.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.platform.ai.core.enums.MessageRole;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 会话消息记录
 *
 * @author xiao1
 * @since 2025-10
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_conversation_turn", autoResultMap = true)
@Schema(description = "会话消息记录")
public class ConversationTurn extends SuperEntity<Long> {

    @Schema(description = "会话ID，关联 ai_conversation")
    private Long conversationId;

    @Schema(description = "上一轮交互ID，用于消息分叉、重试或上下文回溯")
    private Long previousTurnId;

    @Schema(description = "发起该轮交互的用户ID")
    private Long userId;

    @Schema(description = "租户ID，用于多租户隔离")
    private Long tenantId;

    @Schema(description = "消息角色（SYSTEM / USER / ASSISTANT / TOOL / FUNCTION / OBSERVATION）")
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

    @Schema(description = "模型提供商（如 OPENAI / ANTHROPIC / AZURE / LOCAL）")
    private AiProvider modelProvider;

    @Schema(description = "模型名称（如 gpt-4.1 / claude-3.5-sonnet）")
    private String modelName;

    @Schema(description = "输入 Token 数（最终 Prompt 的 Token 数）")
    private Integer inputTokens;

    @Schema(description = "输出 Token 数（模型生成内容的 Token 数）")
    private Integer outputTokens;

    @Schema(description = "模型推理耗时（毫秒）")
    private Long inferenceLatencyMs;

    @Schema(description = "模型推理辅助摘要信息（不包含完整思维链）")
    private String reasoningSummary;

    @Schema(description = "用户反馈：1-点赞 2-点踩 0-未反馈")
    private Integer userFeedback;

    @Schema(description = "用户反馈备注说明")
    private String feedbackRemark;

    @Schema(description = "扩展属性（JSON 格式，用于存放模型参数、调用配置等）")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    @Schema(description = "会话内序号，用于保证消息顺序")
    private Integer sequenceNum;
}
