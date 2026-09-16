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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.microservice.framework.ai.harness.capability.ResolvedCapabilityScope;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.platform.ai.domain.dto.req.ChatAgentSaveReq;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import com.microservice.platform.ai.repository.ChatAgentMapper;
import com.microservice.platform.ai.service.ConversationService;
import com.microservice.platform.suite.feign.OssFileFeign;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.core.env.Environment;

import java.util.List;
import java.util.Map;

class ChatAgentServiceImplTest {

    @Test
    void createShouldRejectMcpConfigurationOutsideCurrentTenant() {
        AuthenticationContext authentication = mock(AuthenticationContext.class);
        when(authentication.userId()).thenReturn(10L);
        when(authentication.tenantId()).thenReturn(1L);
        ChatAgentMapper chatAgentMapper = mock(ChatAgentMapper.class);
        when(chatAgentMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        ToolsetResolver resolver = mock(ToolsetResolver.class);
        when(resolver.resolve(any(com.microservice.framework.ai.harness.capability.ToolsetSelectionContext.class)))
                .thenReturn(new ResolvedCapabilityScope("scope", List.of(), List.of(),
                        Map.of(), List.of()));
        Environment environment = mock(Environment.class);
        when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
        ChatAgentServiceImpl service = new ChatAgentServiceImpl(authentication, mock(OssFileFeign.class),
                mock(ConversationService.class), resolver, environment);
        ReflectionTestUtils.setField(service, "baseMapper", chatAgentMapper);
        ChatAgentSaveReq request = new ChatAgentSaveReq();
        request.setName("外部数据助手");
        request.setToolsetIds(List.of("mcp:1:7"));

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("Toolset 不存在");
        verify(chatAgentMapper, never()).insert(any(ChatAgent.class));
    }
}
