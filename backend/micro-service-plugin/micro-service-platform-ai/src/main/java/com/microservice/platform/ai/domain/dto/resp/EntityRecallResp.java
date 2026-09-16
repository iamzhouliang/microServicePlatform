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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 实体召回测试响应
 *
 * @author xJh
 * @since 2025/12/18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "实体召回测试响应")
public class EntityRecallResp {

    @Schema(description = "知识库ID")
    private String knowledgeBaseId;

    @Schema(description = "查询关键词")
    private List<String> keywords;

    @Schema(description = "召回的实体列表")
    private List<RecalledEntity> entities;

    @Schema(description = "相关三元组")
    private List<String> triples;

    @Schema(description = "召回实体数量")
    private Integer entityCount;

    @Schema(description = "三元组数量")
    private Integer tripleCount;

    /**
     * 召回的实体
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "召回的实体")
    public static class RecalledEntity {

        @Schema(description = "实体ID")
        private String id;

        @Schema(description = "实体名称")
        private String name;

        @Schema(description = "实体类型")
        private String type;

        @Schema(description = "匹配分数")
        private Double score;
    }
}
