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
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.hibernate.validator.constraints.Length;

import java.util.List;

/**
 * AI技能保存请求
 *
 * @author xJh
 * @since 2026/06/24
 */
@Data
@Schema(description = "AI技能保存请求")
public class SkillSaveReq {

    @NotBlank(message = "技能名称不能为空")
    @Length(max = 100, message = "技能名称长度不能超过{max}")
    @Schema(description = "技能名称")
    private String name;

    @NotBlank(message = "技能编码不能为空")
    @Length(max = 100, message = "技能编码长度不能超过{max}")
    @Schema(description = "技能编码")
    private String code;

    @Length(max = 500, message = "技能描述长度不能超过{max}")
    @Schema(description = "技能描述")
    private String description;

    @Length(max = 50, message = "技能分类长度不能超过{max}")
    @Schema(description = "技能分类")
    private String category;

    @NotBlank(message = "技能版本不能为空")
    @Length(max = 50, message = "技能版本长度不能超过{max}")
    @Pattern(regexp = "^[0-9A-Za-z][0-9A-Za-z._-]*$", message = "技能版本格式不正确")
    @Schema(description = "技能稳定版本", example = "1.0.0")
    private String version = "1.0.0";

    @Schema(description = "依赖的精确工具标识列表，格式为 name@version")
    private List<String> requiresTools = List.of();

    @Schema(description = "依赖的 Toolset 标识列表")
    private List<String> requiresToolsets = List.of();

    @Length(max = 100, message = "图标长度不能超过{max}")
    @Schema(description = "图标")
    private String icon;

    @Schema(description = "标签列表")
    private List<String> tags;

}
