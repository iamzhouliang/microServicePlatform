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

import java.time.Instant;

/**
 * 会话详情响应
 *
 * @author xJh
 * @since 2025/10/30
 */
@Data
@Schema(description = "会话详情响应")
public class ConversationDetailResp {

    @Schema(description = "ID")
    private String id;

    @Schema(description = "会话名称")
    private String title;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "最后一条消息内容")
    private String lastMessage;

    @Schema(description = "消息数量")
    private Integer messageCount;

    @Schema(description = "是否置顶")
    private Boolean pinned;

    @Schema(description = "创建人ID")
    private Long createBy;

    @Schema(description = "创建人名称")
    private String createName;

    @Schema(description = "创建时间")
    private Instant createTime;

    @Schema(description = "最后修改人ID")
    private Long lastModifiedBy;

    @Schema(description = "最后修改人名称")
    private String lastModifiedName;

    @Schema(description = "最后修改时间")
    private Instant lastModifiedTime;
}
