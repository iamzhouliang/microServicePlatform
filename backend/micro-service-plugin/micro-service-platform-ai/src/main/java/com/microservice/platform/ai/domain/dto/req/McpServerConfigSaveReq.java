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
import lombok.Data;
import java.util.Map;
import java.util.List;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;

@Data
@Schema(description = "MCP服务配置保存请求")
public class McpServerConfigSaveReq {

    @NotBlank(message = "服务名称不能为空")
    @Schema(description = "服务名称")
    private String name;

    @NotBlank(message = "连接类型不能为空")
    @Schema(description = "连接类型 (STDIO/SSE)")
    private String type;

    @Schema(description = "STDIO命令")
    private String command;

    @Schema(description = "STDIO参数列表(JSON)")
    private String args;

    @Schema(description = "SSE URL")
    private String url;

    @Schema(description = "环境变量(JSON)")
    private String env;

    @Schema(description = "按原始 Tool 名配置的显式治理策略")
    private Map<String, McpToolGovernancePolicy> toolGovernance;

    @Schema(description = "允许启用该 Toolset 的运行环境")
    private List<String> enabledEnvironments;

    @Schema(description = "启用该 Toolset 所需的权限码")
    private List<String> requiredPermissions;

    @Schema(description = "状态")
    private Boolean status;
}
