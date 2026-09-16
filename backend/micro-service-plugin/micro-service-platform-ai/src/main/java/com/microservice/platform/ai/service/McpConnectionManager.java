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

package com.microservice.platform.ai.service;

import dev.langchain4j.mcp.client.McpClient;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MCP连接管理器
 *
 * @author xJh
 * @since 2025/12/07
 */
public interface McpConnectionManager {

    /**
     * 获取或创建MCP客户端
     *
     * @param tenantId 可信租户 ID
     * @param configId 配置ID
     * @return McpClient 实例
     */
    McpClient getClient(Long tenantId, Long configId);

    /**
     * 获取带引用计数的客户端租约。工具执行必须使用租约，刷新连接时会等待在途调用释放后再关闭。
     * @param configId configId 参数
     * @param tenantId 租户标识
     * @return 处理结果
     */
    default ClientLease leaseClient(Long tenantId, Long configId) {
        return new ClientLease(getClient(tenantId, configId), () -> {
        });
    }

    /**
     * 关闭并移除客户端
     *
     * @param configId 配置ID
     * @param tenantId 租户标识
     */
    void closeClient(Long tenantId, Long configId);

    /**
     * 刷新连接
     *
     * @param configId 配置ID
     * @param tenantId 租户标识
     */
    void refreshClient(Long tenantId, Long configId);

    /**
     * 查询当前租户 MCP Tool 的显式治理策略，未配置时默认拒绝暴露。
     * @param configId configId 参数
     * @param tenantId 租户标识
     * @param toolName 工具名称
     * @return 处理结果
     */
    Optional<McpToolGovernancePolicy> findToolGovernance(Long tenantId, Long configId, String toolName);

    /** MCP 客户端在途调用租约。 */
    final class ClientLease implements AutoCloseable {

        private final McpClient client;
        private final Runnable release;
        private final AtomicBoolean closed = new AtomicBoolean();

        public ClientLease(McpClient client, Runnable release) {
            this.client = Objects.requireNonNull(client, "MCP 客户端不能为空");
            this.release = Objects.requireNonNull(release, "MCP 租约释放器不能为空");
        }

        public McpClient client() {
            return client;
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                release.run();
            }
        }
    }
}
