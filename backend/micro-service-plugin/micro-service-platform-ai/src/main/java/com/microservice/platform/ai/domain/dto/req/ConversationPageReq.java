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

import com.microservice.framework.db.mybatisplus.page.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import java.util.List;

/**
 * @author xJh
 * @since 2025/10/30
 **/
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "ConversationPageReq", description = "会话分页查询")
public class ConversationPageReq extends PageRequest {

    @Schema(description = "会话名称")
    private String title;

    @Schema(description = "模型名称")
    private String modelName;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "对话类型")
    private Integer type;

    @Schema(description = "对话类型集合；用于兼容同一入口的历史会话")
    private List<Integer> types;

    @Schema(description = "智能体ID")
    private Long agentId;
}
