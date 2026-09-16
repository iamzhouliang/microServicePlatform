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

import com.microservice.platform.ai.core.enums.ConversationType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.Instant;
import java.util.List;

/**
 * @author xJh
 * @since 2025/10/30
 **/
@Data
@Schema(description = "会话分页响应")
public class ConversationPageResp {

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "会话名称")
    private String title;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "关联的知识库ID列表")
    private List<Long> knowledgeBaseIds;

    @Schema(description = "智能体ID")
    private Long agentId;

    @Schema(description = "对话类型")
    private ConversationType type;

    @Schema(description = "最后一条消息内容")
    private String lastMessage;

    @Schema(description = "消息数量")
    private Integer messageCount;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "创建时间")
    private Instant createTime;

    @Schema(description = "最后修改时间")
    private Instant lastModifyTime;

    @Schema(description = "租户ID")
    private String tenantId;

}
