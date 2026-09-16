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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolExecutor;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GovernedToolExecutorTest {
    
    @Test
    void shouldRejectMissingPermissionWithoutInvokingDelegate() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        GovernedToolExecutor executor = new GovernedToolExecutor(governance(), delegate);
        InvocationParameters parameters = InvocationParameters.from(HarnessInvocation.PARAMETER_KEY,
                new HarnessInvocation("tenant-1", "user-1", "conversation-1", "operation-1", "turn-1",
                        Set.of("iam:user:view")));
        InvocationContext context = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1").invocationParameters(parameters).build();
        
        ToolExecutionResult result = executor.executeWithContext(request(), context);
        
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("没有执行该操作的权限");
        verify(delegate, never()).executeWithContext(request(), context);
    }
    
    @Test
    void shouldRejectInvocationWithoutTrustedActorContext() {
        ToolExecutor delegate = mock(ToolExecutor.class);
        GovernedToolExecutor executor = new GovernedToolExecutor(governance(), delegate);
        InvocationContext context = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1").build();
        
        ToolExecutionResult result = executor.executeWithContext(request(), context);
        
        assertThat(result.isError()).isTrue();
        assertThat(result.resultText()).contains("缺少可信调用身份");
        verify(delegate, never()).executeWithContext(request(), context);
    }
    
    private static ToolGovernance governance() {
        return new ToolGovernance("provision_user", "module:iam:user-management", "iam", "1.0.0",
                "iam:user:create",
                HarnessRiskLevel.HIGH, true, true, true, false, "verify_user", null);
    }
    
    private static ToolExecutionRequest request() {
        return ToolExecutionRequest.builder().id("call-1").name("provision_user").arguments("{}").build();
    }
}
