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
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.platform.ai.core.enums.ChunkType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 向量元数据
 * 用于存储向量在向量数据库中的元数据信息
 *
 * @author xJh
 * @since 2025/10/20
 */
@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_vector_metadata", autoResultMap = true)
@Schema(description = "向量元数据")
public class VectorMetadata extends SuperEntity<Long> {

    @Schema(description = "向量ID（在向量数据库中的唯一标识）")
    private String vectorId;

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "关联的知识条目ID")
    private Long itemId;

    @Schema(description = "关联的知识分片ID")
    private Long chunkId;

    @Schema(description = "分片类型")
    private ChunkType chunkType;

    @Schema(description = "向量数据库集合名称")
    private String collectionName;

    @Schema(description = "文本内容")
    private String textContent;

    @Schema(description = "文本哈希值")
    private String textHash;

    @Schema(description = "相似度分数")
    private Double similarityScore;

    @Schema(description = "扩展元数据")
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @Schema(description = "租户ID")
    private Long tenantId;
}
