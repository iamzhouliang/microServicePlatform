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

package com.microservice.platform.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigPageReq;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigSaveReq;
import com.microservice.platform.ai.domain.dto.resp.McpConnectionTestResp;
import com.microservice.platform.ai.domain.dto.resp.McpServerConfigPageResp;
import com.microservice.platform.ai.domain.dto.resp.McpToolInfoResp;
import com.microservice.platform.ai.domain.entity.McpServer;
import com.microservice.platform.ai.repository.McpServerMapper;
import com.microservice.platform.ai.service.McpConnectionManager;
import com.microservice.platform.ai.service.McpServerConfigService;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * MCP服务器配置服务实现
 *
 * @author xJh
 * @since 2025/12/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpServerConfigServiceImpl extends SuperServiceImpl<McpServerMapper, McpServer> implements McpServerConfigService {

    private final McpConnectionManager mcpConnectionManager;
    private final AuthenticationContext context;

    @Override
    public IPage<McpServerConfigPageResp> pageList(McpServerConfigPageReq req) {
        return this.page(req.buildPage(), Wraps.<McpServer>lbQ()
                .like(McpServer::getName, req.getName())
                .eq(McpServer::getStatus, req.getStatus())
                .orderByDesc(McpServer::getId))
                .convert(x -> {
                    McpServerConfigPageResp resp = BeanUtil.toBean(x, McpServerConfigPageResp.class);
                    // env 通常承载 MCP Server 的 API Key/Token 等凭证，脱敏后再返回前端，保留 key 便于展示
                    resp.setEnv(maskEnv(resp.getEnv()));
                    return resp;
                });
    }

    /**
     * 脱敏环境变量值（保留 key，值统一替换为掩码），避免向前端明文回显凭证。
     * @param env env 参数
     * @return 处理结果
     */
    private java.util.Map<String, String> maskEnv(java.util.Map<String, String> env) {
        if (env == null || env.isEmpty()) {
            return env;
        }
        java.util.Map<String, String> masked = new java.util.LinkedHashMap<>();
        env.forEach((key, value) -> masked.put(key, value == null || value.isEmpty() ? value : "******"));
        return masked;
    }

    @Override
    public void create(McpServerConfigSaveReq req) {
        McpServer config = BeanUtil.toBean(req, McpServer.class);
        this.baseMapper.insert(config);
    }

    @Override
    public void modify(Long id, McpServerConfigSaveReq req) {
        Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("MCP配置不存在"));
        McpServer config = BeanUtilPlus.toBean(id, req, McpServer.class);
        this.baseMapper.updateById(config);
        // 刷新连接缓存
        mcpConnectionManager.refreshClient(tenantId(), id);
    }

    @Override
    public void remove(Long id) {
        this.baseMapper.deleteById(id);
        // 关闭连接
        mcpConnectionManager.closeClient(tenantId(), id);
    }

    @Override
    public List<McpServer> listEnabled() {
        return this.list(Wraps.<McpServer>lbQ()
                .eq(McpServer::getStatus, true));
    }

    @Override
    public void refresh(Long id) {
        mcpConnectionManager.refreshClient(tenantId(), id);
    }

    @Override
    public McpConnectionTestResp testConnection(Long id) {
        long startTime = System.currentTimeMillis();

        try {
            // 先关闭已有连接
            mcpConnectionManager.refreshClient(tenantId(), id);

            // 创建新连接
            McpClient client = mcpConnectionManager.getClient(tenantId(), id);

            // 获取工具列表（验证连接是否真正可用）
            List<ToolSpecification> tools = client.listTools();

            long responseTime = System.currentTimeMillis() - startTime;

            McpServer config = Optional.ofNullable(this.baseMapper.selectById(id))
                    .orElseThrow(() -> CheckedException.notFound("MCP配置不存在"));

            return McpConnectionTestResp.builder()
                    .success(true)
                    .serverName(config.getName())
                    .toolCount(tools != null ? tools.size() : 0)
                    .responseTime(responseTime)
                    .build();

        } catch (Exception e) {
            log.error("MCP连接测试失败: id={}", id, e);
            long responseTime = System.currentTimeMillis() - startTime;

            return McpConnectionTestResp.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .responseTime(responseTime)
                    .build();
        }
    }

    @Override
    public List<McpToolInfoResp> getTools(Long id) {
        McpClient client = mcpConnectionManager.getClient(tenantId(), id);
        List<ToolSpecification> toolSpecs = client.listTools();

        if (toolSpecs == null || toolSpecs.isEmpty()) {
            return new ArrayList<>();
        }

        List<McpToolInfoResp> result = new ArrayList<>();
        for (ToolSpecification spec : toolSpecs) {
            McpToolInfoResp toolInfo = McpToolInfoResp.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .build();
            result.add(toolInfo);
        }

        return result;
    }

    @Override
    public void toggleStatus(Long id, Boolean status) {
        McpServer config = Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("MCP配置不存在"));

        config.setStatus(status);
        this.baseMapper.updateById(config);

        // 如果禁用，关闭连接
        if (Boolean.FALSE.equals(status)) {
            mcpConnectionManager.closeClient(tenantId(), id);
        }
    }

    private Long tenantId() {
        Long tenantId = context.tenantId();
        if (tenantId == null || tenantId <= 0) {
            throw CheckedException.badRequest("认证上下文缺少有效租户信息");
        }
        return tenantId;
    }
}
