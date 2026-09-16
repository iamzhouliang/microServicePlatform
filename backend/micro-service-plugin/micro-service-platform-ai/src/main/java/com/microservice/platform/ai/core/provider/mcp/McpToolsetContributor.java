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

package com.microservice.platform.ai.core.provider.mcp;

import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetContributor;
import com.microservice.framework.ai.harness.capability.ToolsetDescriptor;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.entity.McpServer;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;
import com.microservice.platform.ai.repository.McpServerMapper;
import com.microservice.platform.ai.service.McpConnectionManager;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.mcp.McpToolExecutor;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 每台 MCP Server 动态形成一个租户隔离的独立 Toolset。 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class McpToolsetContributor implements ToolsetContributor {

    private static final String PREFIX = "mcp:";

    private final McpConnectionManager connectionManager;
    private final McpServerMapper serverMapper;

    @Override
    public Collection<ToolsetContribution> contribute(ToolsetSelectionContext context) {
        if (context.requestedToolsets().isEmpty()) {
            return List.of();
        }
        List<ToolsetContribution> contributions = new ArrayList<>();
        context.requestedToolsets().stream()
                .map(id -> parseServerId(id, context.tenantId()))
                .flatMap(Optional::stream)
                .distinct()
                .forEach(serverId -> load(context, serverId).ifPresent(contributions::add));
        return List.copyOf(contributions);
    }

    private Optional<ToolsetContribution> load(ToolsetSelectionContext context, Long serverId) {
        Long tenantId = Long.valueOf(context.tenantId());
        McpServer server = serverMapper.selectOne(Wraps.<McpServer>lbQ()
                .eq(McpServer::getTenantId, tenantId)
                .eq(McpServer::getId, serverId)
                .eq(McpServer::getStatus, true));
        if (server == null) {
            return Optional.empty();
        }
        try (McpConnectionManager.ClientLease lease = connectionManager.leaseClient(tenantId, serverId)) {
            McpClient client = lease.client();
            List<ToolSpecification> specifications = client.listTools();
            if (specifications == null) {
                return Optional.empty();
            }
            Map<String, ToolGovernance> governance = new LinkedHashMap<>();
            List<AiServiceTool> tools = specifications.stream()
                    .map(specification -> toTool(tenantId, serverId, specification, governance))
                    .flatMap(Optional::stream)
                    .toList();
            ToolsetDescriptor descriptor = new ToolsetDescriptor(toolsetId(tenantId, serverId), schemaSetVersion(governance),
                    ToolsetSource.MCP, "MCP 服务：" + server.getName(), Set.of("BUSINESS"), Set.of(),
                    set(server.getEnabledEnvironments()), Set.of(), set(server.getRequiredPermissions()), true);
            return Optional.of(new ToolsetContribution(descriptor, tools, governance));
        } catch (RuntimeException exception) {
            log.warn("MCP Toolset 加载失败，已按失败关闭处理：tenantId={}, serverId={}",
                    tenantId, serverId, exception);
            return Optional.empty();
        }
    }

    private Optional<AiServiceTool> toTool(Long tenantId, Long serverId, ToolSpecification original,
                                           Map<String, ToolGovernance> governance) {
        Optional<McpToolGovernancePolicy> policy = connectionManager.findToolGovernance(
                tenantId, serverId, original.name());
        if (policy.isEmpty()) {
            log.warn("MCP Tool 未配置显式治理策略，默认不暴露：serverId={}, toolName={}",
                    serverId, original.name());
            return Optional.empty();
        }
        String exposedName = "mcp_" + serverId + "_" + original.name();
        ToolSpecification exposed = original.toBuilder().name(exposedName).build();
        ToolExecutor executor = new LeasedMcpToolExecutor(connectionManager, tenantId, serverId, original.name());
        AiServiceTool tool = ToolProviderResult.builder().add(exposed, executor).build().aiServiceTools().getFirst();
        McpToolGovernancePolicy rule = policy.orElseThrow();
        String digest = schemaDigest(original);
        governance.put(exposedName, new ToolGovernance(exposedName, toolsetId(tenantId, serverId),
                "mcp-" + serverId, digest,
                rule.requiredPermission(), rule.risk(), rule.write(), rule.idempotent(),
                rule.confirmationRequired(), rule.approvalRequired(), rule.verifier(), rule.compensator()));
        return Optional.of(tool);
    }

    public static String toolsetId(Long tenantId, Long serverId) {
        return PREFIX + tenantId + ":" + serverId;
    }

    private static Optional<Long> parseServerId(String toolsetId, String tenantId) {
        String expectedPrefix = PREFIX + tenantId + ":";
        if (toolsetId == null || !toolsetId.startsWith(expectedPrefix)) {
            return Optional.empty();
        }
        try {
            long serverId = Long.parseLong(toolsetId.substring(expectedPrefix.length()));
            return serverId > 0 ? Optional.of(serverId) : Optional.empty();
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    private static Set<String> set(Collection<String> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    private static String schemaSetVersion(Map<String, ToolGovernance> governance) {
        String source = governance.values().stream().map(ToolGovernance::identity).sorted().toList().toString();
        return sha256(source);
    }

    private static String schemaDigest(ToolSpecification specification) {
        return sha256(specification.name() + "\n" + specification.description() + "\n" + specification.parameters());
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", impossible);
        }
    }

    /**
     * 每次执行单独持有客户端租约，配置刷新不会中断在途调用。
     */
    private static final class LeasedMcpToolExecutor implements ToolExecutor {

        private final McpConnectionManager manager;
        private final Long tenantId;
        private final Long serverId;
        private final String originalToolName;

        private LeasedMcpToolExecutor(
                                      McpConnectionManager manager, Long tenantId, Long serverId, String originalToolName) {
            this.manager = manager;
            this.tenantId = tenantId;
            this.serverId = serverId;
            this.originalToolName = originalToolName;
        }

        @Override
        public String execute(ToolExecutionRequest request, Object memoryId) {
            try (McpConnectionManager.ClientLease lease = manager.leaseClient(tenantId, serverId)) {
                return new McpToolExecutor(lease.client(), originalToolName).execute(request, memoryId);
            }
        }

        @Override
        public ToolExecutionResult executeWithContext(ToolExecutionRequest request, InvocationContext context) {
            try (McpConnectionManager.ClientLease lease = manager.leaseClient(tenantId, serverId)) {
                return new McpToolExecutor(lease.client(), originalToolName).executeWithContext(request, context);
            }
        }
    }
}
