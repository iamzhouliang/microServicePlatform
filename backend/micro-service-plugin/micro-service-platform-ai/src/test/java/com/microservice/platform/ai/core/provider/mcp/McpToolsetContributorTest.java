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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microservice.framework.ai.harness.capability.DefaultToolsetAvailabilityPolicy;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.platform.ai.domain.entity.McpServer;
import com.microservice.platform.ai.domain.model.McpToolGovernancePolicy;
import com.microservice.platform.ai.repository.McpServerMapper;
import com.microservice.platform.ai.service.McpConnectionManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class McpToolsetContributorTest {

    @Test
    void eachServerShouldFormAnIndependentTenantToolset() {
        McpClient client = mock(McpClient.class);
        ToolSpecification specification = ToolSpecification.builder().name("lookup")
                .description("查询外部数据").parameters(JsonObjectSchema.builder().build()).build();
        when(client.listTools()).thenReturn(List.of(specification));
        McpConnectionManager manager = mock(McpConnectionManager.class);
        when(manager.leaseClient(1L, 7L)).thenAnswer(invocation -> new McpConnectionManager.ClientLease(client, () -> {
        }));
        when(manager.findToolGovernance(1L, 7L, "lookup")).thenReturn(Optional.of(
                new McpToolGovernancePolicy("ai:mcp:execute", HarnessRiskLevel.LOW, false,
                        true, false, false, null, null)));
        McpServerMapper mapper = mock(McpServerMapper.class);
        when(mapper.selectOne(any())).thenReturn(McpServer.builder().id(7L).tenantId(1L).name("ERP")
                .status(true).enabledEnvironments(List.of("prod"))
                .requiredPermissions(List.of("ai:mcp:execute")).build());
        McpToolsetContributor contributor = new McpToolsetContributor(manager, mapper);
        ToolsetResolver resolver = new ToolsetResolver(List.of(contributor),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        ToolsetSelectionContext context = new ToolsetSelectionContext("1", "2", "conversation-1", "turn-1",
                "BUSINESS", "QUERY", "prod", Set.of("ai:mcp:execute"), Set.of("mcp:1:7"), Set.of());

        var scope = resolver.resolve(context);

        assertThat(scope.selectedToolsets()).singleElement().satisfies(descriptor -> {
            assertThat(descriptor.id()).isEqualTo("mcp:1:7");
            assertThat(descriptor.source()).isEqualTo(ToolsetSource.MCP);
            assertThat(descriptor.dynamic()).isTrue();
        });
        assertThat(scope.tools()).singleElement().satisfies(tool -> {
            assertThat(tool.name()).isEqualTo("mcp_7_lookup");
            assertThat(scope.resolve(tool.toolSpecification()).orElseThrow().identity())
                    .startsWith("mcp-7:mcp_7_lookup@");
        });
    }

    @Test
    void shouldNotDiscoverMcpServerOutsideRequestedTenantScope() {
        McpConnectionManager manager = mock(McpConnectionManager.class);
        McpServerMapper mapper = mock(McpServerMapper.class);
        McpToolsetContributor contributor = new McpToolsetContributor(manager, mapper);
        ToolsetSelectionContext context = new ToolsetSelectionContext("1", "2", "conversation-1", "turn-1",
                "BUSINESS", "QUERY", "prod", Set.of(), Set.of("mcp:9:7"), Set.of());

        assertThat(contributor.contribute(context)).isEmpty();
        verify(mapper, never()).selectOne(any());
        verify(manager, never()).leaseClient(any(), any());
    }
}
