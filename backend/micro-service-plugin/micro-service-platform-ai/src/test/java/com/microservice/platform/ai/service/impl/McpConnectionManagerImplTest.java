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

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.microservice.platform.ai.repository.McpServerMapper;
import com.microservice.platform.ai.service.McpConnectionManager.ClientLease;
import dev.langchain4j.mcp.client.McpClient;
import java.lang.reflect.Constructor;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MCP 客户端刷新与在途租约生命周期测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class McpConnectionManagerImplTest {

    @Test
    void shouldCloseRetiredClientOnlyAfterInflightLeaseIsReleased() throws Exception {
        McpConnectionManagerImpl manager = new McpConnectionManagerImpl(mock(McpServerMapper.class));
        McpClient client = mock(McpClient.class);
        cachedClients(manager).put(cacheKey(1L, 7L), clientHolder(1L, 7L, client));

        ClientLease lease = manager.leaseClient(1L, 7L);
        manager.refreshClient(1L, 7L);

        verify(client, never()).close();
        lease.close();
        verify(client).close();
        lease.close();
        verify(client, times(1)).close();
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> cachedClients(McpConnectionManagerImpl manager) {
        return (Map<Object, Object>) ReflectionTestUtils.getField(manager, "clientCache");
    }

    private static Object cacheKey(Long tenantId, Long configId) throws Exception {
        Class<?> type = Class.forName(McpConnectionManagerImpl.class.getName() + "$ClientKey");
        Constructor<?> constructor = type.getDeclaredConstructor(Long.class, Long.class);
        constructor.setAccessible(true);
        return constructor.newInstance(tenantId, configId);
    }

    private static Object clientHolder(Long tenantId, Long configId, McpClient client) throws Exception {
        Class<?> type = Class.forName(McpConnectionManagerImpl.class.getName() + "$ClientHolder");
        Constructor<?> constructor = type.getDeclaredConstructor(Long.class, Long.class, McpClient.class, Map.class);
        constructor.setAccessible(true);
        return constructor.newInstance(tenantId, configId, client, Map.of());
    }
}
