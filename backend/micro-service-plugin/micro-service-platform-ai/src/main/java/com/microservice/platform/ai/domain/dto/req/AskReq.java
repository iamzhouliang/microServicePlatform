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

import com.microservice.platform.ai.core.enums.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Map;

/**
 * AI对话请求参数
 *
 * @author xJh
 * @since 2025/10/11
 */
@Data
@Schema(description = "AI对话请求参数")
public class AskReq {

    @Schema(description = "对话类型 1：普通对话 2：通用智能体对话 3.平台智能体（客服等定制的）4：知识库对话 5.图片生成")
    private ConversationType chatType;

    @Schema(description = "模型名称 (1.普通对话）")
    private String modelId;

    @Schema(description = "智能体id（2.通用智能体对话 3.平台智能体对话）")
    private Long agentId;

    @Schema(description = "知识库id（4.知识库对话）")
    private Long kbId;

    @Schema(description = "会话ID")
    private Long conversationId;

    @Schema(description = "提示词")
    @NotBlank(message = "提示词不能为空")
    @Size(max = 20000, message = "提示词长度不能超过20000个字符")
    private String prompt;

    @Schema(description = "联网搜索", defaultValue = "false")
    private Boolean enableWebSearch;

    @Schema(description = "深度思考", defaultValue = "false")
    private Boolean returnThinking;

    @Schema(description = "RAG元数据过滤（文档类型、文档id等）")
    private Map<String, String> metadataFilter;

    @Schema(description = "图片地址")
    private String imageUrl;

    @Schema(description = "负面提示词（当chatType=5时使用）")
    private String negativePrompt;

    @Schema(description = "图片尺寸（当chatType=5时使用）")
    private String size;

    @Schema(description = "推理步数（当chatType=5时使用）")
    private Integer steps;

    @Schema(description = "引导系数（当chatType=5时使用）")
    private Float scale;

    @Schema(description = "随机种子（当chatType=5时使用）")
    private Integer seed;

    @Schema(description = "提示词扩展（当chatType=5时使用）")
    private Boolean promptExtend;

    @Schema(description = "水印（当chatType=5时使用）")
    private Boolean watermark;

    @Schema(description = "参考图片地址（当chatType=5时使用）")
    private String refImageUrl;

    @Schema(description = "异步生成（当chatType=5时使用）")
    private Boolean async;
}
