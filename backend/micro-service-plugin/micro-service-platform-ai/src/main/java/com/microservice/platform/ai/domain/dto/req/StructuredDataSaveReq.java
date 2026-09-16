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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Map;

/**
 * 结构化数据保存请求
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Data
@Schema(description = "结构化数据保存请求")
public class StructuredDataSaveReq {

    @NotBlank(message = "知识库ID不能为空")
    @Schema(description = "所属知识库ID")
    private Long kbId;

    @NotBlank(message = "数据类型不能为空")
    @Schema(description = "数据类型：product_param, policy, rule等")
    private String dataType;

    @NotBlank(message = "数据标题不能为空")
    @Schema(description = "数据标题/名称")
    private String title;

    @NotNull(message = "结构化数据内容不能为空")
    @Schema(description = "结构化数据内容（JSON格式）")
    private Map<String, Object> content;

    @Schema(description = "元数据")
    private Map<String, Object> metadata;
}
