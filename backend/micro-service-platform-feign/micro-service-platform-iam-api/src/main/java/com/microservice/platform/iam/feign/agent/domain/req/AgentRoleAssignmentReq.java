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

package com.microservice.platform.iam.feign.agent.domain.req;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * @author OmX
 * @since 2026-07-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRoleAssignmentReq {

    @Schema(description = "Harness 注入的幂等操作键", hidden = true)
    @NotBlank(message = "幂等操作键不能为空")
    @Size(max = 128, message = "幂等操作键长度不能超过{max}")
    @Pattern(regexp = "^[A-Za-z0-9._:@-]+$", message = "幂等操作键格式错误")
    private String operationKey;

    @Schema(description = "角色ID列表")
    @NotNull(message = "角色ID列表不能为空")
    private List<Long> roleIds;

    @Schema(description = "执行补偿时要求用户当前仍持有的角色ID列表；为空表示普通覆盖")
    private List<Long> expectedCurrentRoleIds;

}
