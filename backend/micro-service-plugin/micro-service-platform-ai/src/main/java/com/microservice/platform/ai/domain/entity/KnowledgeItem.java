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
import com.baomidou.mybatisplus.annotation.Version;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 知识条目
 * 统一管理不同类型的知识，包括文档、问答对、结构化数据等
 *
 * @author xJh
 * @since 2025/10/20
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_knowledge_item", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "知识条目")
public class KnowledgeItem extends SuperEntity<Long> {

    @Schema(description = "所属知识库ID")
    private Long kbId;

    @Schema(description = "知识条目类型")
    private KnowledgeItemType type;

    @Schema(description = "标题")
    private String title;

    @Schema(description = "问题（仅当类型为问答对时有效）")
    private String question;

    @Schema(description = "答案（仅当类型为问答对时有效）")
    private String answer;

    @Schema(description = "原始内容")
    private String content;

    @Schema(description = "内容类型（如 pdf、text、html）")
    private String contentType;

    @Schema(description = "文件路径")
    private String filePath;

    @Schema(description = "文件大小（字节）")
    private Long fileSize;

    @Schema(description = "内容哈希值（用于去重与变更检测）")
    private String contentHash;

    @Schema(description = "是否已向量化")
    private Boolean vectorized;

    @Schema(description = "是否已图谱化")
    private Boolean graphed;

    @Schema(description = "处理状态")
    private KnowledgeItemStatus status;

    @Schema(description = "扩展元数据")
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    @Version
    @Schema(description = "乐观锁版本")
    private Integer version;

    @Schema(description = "租户ID")
    private Long tenantId;
}
