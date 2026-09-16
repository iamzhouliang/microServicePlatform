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

import com.microservice.framework.db.mybatisplus.page.PageRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI技能分页请求
 *
 * @author xJh
 * @since 2026/06/24
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI技能分页请求")
public class SkillPageReq extends PageRequest {

    @Schema(description = "技能名称")
    private String name;

    @Schema(description = "技能编码")
    private String code;

    @Schema(description = "技能分类")
    private String category;

    @Schema(description = "是否启用")
    private Boolean status;

    @Schema(description = "是否发布")
    private Boolean published;
}
