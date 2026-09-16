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
import java.util.List;
import lombok.Data;

/**
 * @author xJh
 * @since 2025/11/4
 **/
@Data
@Schema(description = "智能体详情响应")
public class ChatAgentDetailResp {

    @Schema(description = "智能体ID")
    private Long id;

    @Schema(description = "用户ID")
    private Long userId;

    @Schema(description = "智能体名称")
    private String name;

    @Schema(description = "绑定模型名称")
    private Long chatModelId;

    @Schema(description = "智能体描述")
    private String description;

    @Schema(description = "智能体角色预设")
    private String systemPrompt;

    @Schema(description = "智能体头像")
    private String avatar;

    @Schema(description = "关联知识库ID")
    private Long kbId;

    @Schema(description = "智能体启用的 Toolset 标识列表")
    private List<String> toolsetIds;

}
