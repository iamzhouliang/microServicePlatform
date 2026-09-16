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

import java.util.Map;

/**
 * 向量化状态响应
 * 纯响应 DTO，不携带 MyBatis 持久层注解（由 Service 通过 BeanUtil 从实体转换填充）。
 *
 * @author xiao1
 * @since 2025-12
 */
@Data
@Schema(description = "向量化状态响应")
public class VectorizationResp {

    @Schema(description = "任务ID")
    private String taskId;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "知识条目ID")
    private Long itemId;

    @Schema(description = "任务类型（SINGLE/BATCH/DOCUMENT/FAQ/STRUCTURED/KNOWLEDGE_ITEM）")
    private String taskType;

    @Schema(description = "是否已向量化")
    private Boolean vectorized;

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "结果向量ID列表（JSON格式）")
    private Map<String, Object> vectorIds;

    @Schema(description = "错误信息")
    private String errorMessage;
}
