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
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.framework.db.mybatisplus.handler.type.StringListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * AI 技能配置
 *
 * @author xJh
 * @since 2026/06/24
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_skill", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI技能配置")
public class AiSkill extends SuperEntity<Long> {

    @Schema(description = "技能名称")
    private String name;

    @Schema(description = "技能编码")
    private String code;

    @Schema(description = "技能描述")
    private String description;

    @Schema(description = "技能分类")
    private String category;

    @Builder.Default
    @Schema(description = "技能稳定版本")
    private String version = "1.0.0";

    @Builder.Default
    @Schema(description = "依赖的精确工具标识列表，格式为 name@version")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> requiresTools = List.of();

    @Builder.Default
    @Schema(description = "依赖的 Toolset 标识列表")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> requiresToolsets = List.of();

    @Schema(description = "SKILL.md UTF-8 正文的 SHA-256 摘要")
    private String contentDigest;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "标签列表")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> tags;

    @Schema(description = "技能目录相对路径")
    private String skillPath;

    @Schema(description = "SKILL.md 相对路径")
    private String skillFile;

    @Schema(description = "资源文件数量")
    private Integer resourceCount;

    @Schema(description = "是否启用")
    private Boolean status;

    @Schema(description = "是否发布")
    private Boolean published;

    @Schema(description = "租户ID")
    private Long tenantId;
}
