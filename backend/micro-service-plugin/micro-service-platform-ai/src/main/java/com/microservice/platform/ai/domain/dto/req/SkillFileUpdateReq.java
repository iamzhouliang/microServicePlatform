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
import org.hibernate.validator.constraints.Length;

/**
 * AI技能文件更新请求.
 *
 * @author xJh
 * @since 2026/06/30
 */
@Data
@Schema(description = "AI技能文件更新请求")
public class SkillFileUpdateReq {

    @NotBlank(message = "文件路径不能为空")
    @Length(max = 500, message = "文件路径长度不能超过{max}")
    @Schema(description = "技能目录内相对文件路径")
    private String path;

    @NotNull(message = "文件内容不能为空")
    @Schema(description = "文件内容")
    private String content;
}
