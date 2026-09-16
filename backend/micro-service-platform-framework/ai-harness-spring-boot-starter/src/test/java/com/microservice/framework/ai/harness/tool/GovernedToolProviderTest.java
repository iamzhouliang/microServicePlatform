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

package com.microservice.framework.ai.harness.tool;

import static org.assertj.core.api.Assertions.assertThat;
import dev.langchain4j.agent.tool.ReturnBehavior;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.service.tool.ToolService;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GovernedToolProviderTest {
    
    @Test
    void shouldReuseOfficialToolDiscoveryAndPreserveLangChain4jMetadata() {
        DomainTools bean = new DomainTools();
        AiServiceTool original = ToolService.findTools(bean).getFirst().toBuilder()
                .returnBehavior(ReturnBehavior.IMMEDIATE_IF_LAST)
                .build();
        ToolProvider delegate = request -> ToolProviderResult.builder().add(original).build();
        ToolGovernance governance = governance("find_user");
        
        ToolProviderResult result = new GovernedToolProvider(delegate,
                specification -> Optional.of(governance)).provideTools(request());
        
        AiServiceTool decorated = result.aiServiceTools().getFirst();
        assertThat(decorated.toolSpecification()).isSameAs(original.toolSpecification());
        assertThat(decorated.returnBehavior()).isEqualTo(ReturnBehavior.IMMEDIATE_IF_LAST);
        assertThat(decorated.toolExecutor()).isInstanceOf(GovernedToolExecutor.class);
    }
    
    @Test
    void shouldDenyUnknownDynamicToolThroughTheSameDecorator() {
        ToolSpecification specification = ToolSpecification.builder().name("remote_write")
                .description("远程写操作")
                .parameters(JsonObjectSchema.builder().build())
                .build();
        ToolExecutor delegateExecutor = (request, memoryId) -> "不应执行";
        ToolProvider dynamic = new ToolProvider() {
            
            @Override
            public ToolProviderResult provideTools(ToolProviderRequest request) {
                return ToolProviderResult.builder().add(specification, delegateExecutor).build();
            }
            
            @Override
            public boolean isDynamic() {
                return true;
            }
        };
        
        GovernedToolProvider provider = new GovernedToolProvider(dynamic, ignored -> Optional.empty());
        AiServiceTool decorated = provider.provideTools(request()).aiServiceTools().getFirst();
        ToolExecutionResult result = decorated.toolExecutor().executeWithContext(
                ToolExecutionRequest.builder().id("call-1").name("remote_write").arguments("{}").build(),
                InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                        .methodArguments(List.of()).chatMemoryId("conversation-1").build());
        
        assertThat(provider.isDynamic()).isTrue();
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("缺少治理元数据");
    }
    
    @Test
    void shouldKeepExecutorAlreadyBoundToRequestScopedGovernance() {
        AiServiceTool original = ToolService.findTools(new DomainTools()).getFirst();
        GovernedToolExecutor requestScoped = new GovernedToolExecutor(
                governance("find_user"), original.toolExecutor());
        AiServiceTool bound = original.toBuilder().toolExecutor(requestScoped).build();
        ToolProvider delegate = request -> ToolProviderResult.builder().add(bound).build();
        
        AiServiceTool decorated = new GovernedToolProvider(delegate, ignored -> Optional.empty())
                .provideTools(request()).aiServiceTools().getFirst();
        
        assertThat(decorated.toolExecutor()).isSameAs(requestScoped);
    }
    
    private static ToolProviderRequest request() {
        InvocationContext context = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1").build();
        UserMessage userMessage = UserMessage.from("查询用户");
        return ToolProviderRequest.builder().invocationContext(context).userMessage(userMessage)
                .messages(List.of(userMessage)).build();
    }
    
    private static ToolGovernance governance(String toolName) {
        return new ToolGovernance(toolName, "module:iam:user-management", "iam", "1.0.0", "iam:user:view",
                HarnessRiskLevel.LOW, false, true, false, false, null, null);
    }
    
    private static final class DomainTools {
        
        @Tool(name = "find_user", value = "查询用户")
        public String findUser() {
            return "用户";
        }
    }
    
}
