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

package com.microservice.platform.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigPageReq;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigSaveReq;
import com.microservice.platform.ai.domain.dto.resp.McpConnectionTestResp;
import com.microservice.platform.ai.domain.dto.resp.McpServerConfigPageResp;
import com.microservice.platform.ai.domain.dto.resp.McpToolInfoResp;
import com.microservice.platform.ai.service.McpServerConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * MCP服务配置控制器
 *
 * @author xJh
 * @since 2025/12/07
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/mcp-server")
@RequiredArgsConstructor
@Tag(name = "MCP服务配置", description = "MCP服务配置管理")
public class McpServerController {

    private final McpServerConfigService mcpServerConfigService;

    @PostMapping("/page")
    @Operation(summary = "分页查询")
    public IPage<McpServerConfigPageResp> pageList(@RequestBody McpServerConfigPageReq req) {
        return mcpServerConfigService.pageList(req);
    }

    @AccessLog(module = "MCP服务配置", description = "新增配置")
    @Operation(summary = "新增配置")
    @PostMapping("/create")
    public void create(@Validated @RequestBody McpServerConfigSaveReq req) {
        mcpServerConfigService.create(req);
    }

    @AccessLog(module = "MCP服务配置", description = "修改配置")
    @Operation(summary = "修改配置")
    @PutMapping("/{id}/modify")
    public void modify(@PathVariable Long id, @Validated @RequestBody McpServerConfigSaveReq req) {
        mcpServerConfigService.modify(id, req);
    }

    @AccessLog(module = "MCP服务配置", description = "删除配置")
    @Operation(summary = "删除配置")
    @DeleteMapping("/{id}")
    public void remove(@PathVariable Long id) {
        mcpServerConfigService.remove(id);
    }

    @Operation(summary = "刷新连接")
    @PatchMapping("/{id}/refresh")
    public void refresh(@PathVariable Long id) {
        mcpServerConfigService.refresh(id);
    }

    @Operation(summary = "测试MCP连接", description = "测试指定MCP服务器的连通性，返回连接状态和可用工具数量")
    @PostMapping("/{id}/test-connection")
    public McpConnectionTestResp testConnection(@PathVariable Long id) {
        return mcpServerConfigService.testConnection(id);
    }

    @Operation(summary = "获取MCP工具列表", description = "获取指定MCP服务器提供的所有工具信息")
    @GetMapping("/{id}/tools")
    public List<McpToolInfoResp> getTools(@PathVariable Long id) {
        return mcpServerConfigService.getTools(id);
    }

    @Operation(summary = "切换启用状态", description = "快速启用或禁用MCP服务器")
    @PatchMapping("/{id}/status")
    public void toggleStatus(@PathVariable Long id, @RequestParam Boolean status) {
        mcpServerConfigService.toggleStatus(id, status);
    }
}
