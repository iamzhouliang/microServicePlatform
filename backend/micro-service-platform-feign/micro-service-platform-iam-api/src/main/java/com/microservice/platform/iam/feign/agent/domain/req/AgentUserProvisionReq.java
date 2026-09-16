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

import cn.hutool.core.lang.RegexPool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建用户并分配角色的原子领域命令。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentUserProvisionReq {
    
    @Schema(description = "Harness 注入的幂等操作键", hidden = true)
    @NotBlank(message = "幂等操作键不能为空")
    @Size(max = 128, message = "幂等操作键长度不能超过{max}")
    @Pattern(regexp = "^[A-Za-z0-9._:@-]+$", message = "幂等操作键格式错误")
    private String operationKey;
    
    @NotBlank(message = "账号不能为空")
    @Size(max = 30, message = "账号长度不能超过{max}")
    private String username;
    
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过{max}")
    private String nickname;
    
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = RegexPool.MOBILE, message = "手机号格式错误")
    private String mobile;
    
    @Size(max = 255, message = "邮箱长度不能超过{max}")
    private String email;
    
    @NotNull(message = "组织ID不能为空")
    private Long orgId;
    
    @NotNull(message = "角色ID列表不能为空")
    private List<Long> roleIds;
}
