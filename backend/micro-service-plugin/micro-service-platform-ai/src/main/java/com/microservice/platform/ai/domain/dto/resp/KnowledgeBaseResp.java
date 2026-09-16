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

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * @author xJh
 * @since 2025/10/20
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识库响应")
public class KnowledgeBaseResp {

    @Schema(description = "知识库ID")
    private Long id;

    @Schema(description = "知识库名称")
    private String name;

    @Schema(description = "描述")
    private String description;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "向量集合名称(物理存储表名/索引名)", hidden = true)
    private String collectionName;

    @Schema(description = "Embedding模型ID（创建后不可修改）")
    private Long embedModelId;

    @Schema(description = "单次召回数量(TopK)", defaultValue = "5")
    private Integer topK;

    @Schema(description = "相似度阈值 (0.0-1.0)", defaultValue = "0.6")
    private Double scoreThreshold;

    @Schema(description = "重排序模型ID (Rerank)", nullable = true)
    private Long rerankModelId;

    @Schema(description = "分片大小 (Token)", defaultValue = "512")
    private Integer chunkSize;

    @Schema(description = "分片重叠 (Token)", defaultValue = "64")
    private Integer chunkOverlap;

    @Schema(description = "是否启用知识图谱")
    private Boolean enableGraph;

    @Schema(description = "默认预览对话模型ID")
    private Long chatModelId;

    @Schema(description = "扩展元数据")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @Schema(description = "创建时间")
    private Instant createTime;

    @Schema(description = "文档数量")
    private Long documentCount;

    @Schema(description = "问答对(FAQ)数量")
    private Long faqCount;

    @Schema(description = "结构化数据数量")
    private Long structuredCount;

    @Schema(description = "知识条目总数")
    private Long itemCount;
}
