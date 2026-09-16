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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;

/**
 * MCP 服务器配置分页响应
 * 仅暴露前端需要的业务字段，避免直接返回实体（不泄漏 deleted / tenantId / 审计字段）。
 *
 * @author xJh
 * @since 2025/12/07
 */
@Data
@Schema(description = "MCP 服务器配置分页响应")
public class McpServerConfigPageResp {

    @Schema(description = "主键 ID")
    private Long id;

    @Schema(description = "服务器名称")
    private String name;

    @Schema(description = "连接类型 (STDIO/SSE)")
    private String type;

    @Schema(description = "STDIO 命令")
    private String command;

    @Schema(description = "STDIO 参数列表")
    private List<String> args;

    @Schema(description = "SSE 连接 URL")
    private String url;

    @Schema(description = "环境变量配置")
    private Map<String, String> env;

    @Schema(description = "按原始 Tool 名配置的显式治理策略")
    private Map<String, McpToolGovernancePolicy> toolGovernance;

    @Schema(description = "允许启用该 Toolset 的运行环境")
    private List<String> enabledEnvironments;

    @Schema(description = "启用该 Toolset 所需的权限码")
    private List<String> requiredPermissions;

    @Schema(description = "是否启用")
    private Boolean status;
}
