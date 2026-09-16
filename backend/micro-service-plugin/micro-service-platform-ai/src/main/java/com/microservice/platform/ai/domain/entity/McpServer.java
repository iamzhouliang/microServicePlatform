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
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.framework.db.mybatisplus.handler.type.StringListTypeHandler;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;

/**
 * MCP服务器配置
 *
 * @author xJh
 * @since 2025/12/07
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_mcp_server", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "MCP服务器配置")
public class McpServer extends SuperEntity<Long> {

    @Schema(description = "服务器名称")
    private String name;

    @Schema(description = "连接类型 (STDIO/SSE)")
    private String type;

    @Schema(description = "STDIO命令")
    private String command;

    @Schema(description = "STDIO参数列表")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> args;

    @Schema(description = "SSE连接URL")
    private String url;

    @Schema(description = "环境变量配置")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> env;

    @Schema(description = "按原始 Tool 名配置的显式治理策略")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, McpToolGovernancePolicy> toolGovernance;

    @Schema(description = "允许启用该 Toolset 的运行环境")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> enabledEnvironments;

    @Schema(description = "启用该 Toolset 所需的权限码")
    @TableField(typeHandler = StringListTypeHandler.class)
    private List<String> requiredPermissions;

    @Schema(description = "是否启用")
    private Boolean status;

    @Schema(description = "租户ID")
    private Long tenantId;
}
