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
import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 知识条目响应
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "知识条目响应")
public class KnowledgeItemResp {

    /**
     *  ID
     */
    private Long id;

    /**
     * 所属知识库ID
     */
    @TableField("kb_id")
    private Long kbId;

    /**
     * 知识条目类型（枚举）
     */
    @TableField("item_type")
    private KnowledgeItemType type;

    /**
     * 标题（文档标题或FAQ的展示文本）
     */
    @TableField("title")
    private String title;

    /**
     * 问题，仅当 type = QA_PAIR 有效
     */
    @TableField("question")
    private String question;

    /**
     * 答案，仅当 type = QA_PAIR 有效
     */
    @TableField("answer")
    private String answer;

    /**
     * 原始内容（用于分片与向量化）
     */
    @TableField("content")
    private String content;

    /**
     * 内容类型（如 pdf、text、html），仅对文档类有效
     */
    @TableField("content_type")
    private String contentType;

    /**
     * 文件路径，仅对文档类有效
     */
    @TableField("file_path")
    private String filePath;

    /**
     * 文件大小，仅对文档类有效
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 内容哈希，用于去重与变更检测
     */
    @TableField("content_hash")
    private String contentHash;

    /**
     * 处理状态（枚举）
     */
    @TableField("status")
    private KnowledgeItemStatus status;

    /**
     * 是否已向量化
     */
    @TableField("vectorized")
    private Boolean vectorized;

    private Boolean graphed;

    /**
     * 乐观锁版本
     */
    @TableField("version")
    private Integer version;

    /**
     * 扩展元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @Schema(description = "创建时间")
    private Instant createTime;

    @Schema(description = "更新时间")
    private Instant lastModifyTime;

    @Schema(description = "分片数量")
    private Integer chunkCount;
}
