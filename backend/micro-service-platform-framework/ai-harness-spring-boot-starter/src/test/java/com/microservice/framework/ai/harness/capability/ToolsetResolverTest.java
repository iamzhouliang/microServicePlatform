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

package com.microservice.framework.ai.harness.capability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.service.tool.ToolProviderRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolsetResolverTest {
    
    @Test
    void shouldExposeOnlyAvailableAndAuthorizedTools() {
        UserTools bean = new UserTools();
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of("BUSINESS"), Set.of("USER_ADMIN"), Set.of("prod"),
                Set.of("iam-api"), Set.of("sys:user:page"), false);
        ToolsetContribution contribution = ToolsetContribution.fromBeans(descriptor, List.of(bean), List.of(
                governance("find_user", "sys:user:page"), governance("create_user", "sys:user:add")));
        ToolsetResolver resolver = new ToolsetResolver(List.of(context -> List.of(contribution)),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        ToolsetSelectionContext context = new ToolsetSelectionContext("1", "2", "conversation-1", "turn-1",
                "BUSINESS", "USER_ADMIN", "prod", Set.of("sys:user:page"),
                Set.of("module:iam:user-management"), Set.of("iam-api"));
        
        ResolvedCapabilityScope scope = resolver.resolve(context);
        
        assertThat(scope.selectedToolsets()).extracting(ToolsetDescriptor::id)
                .containsExactly("module:iam:user-management");
        assertThat(scope.toolProvider().provideTools(null).aiServiceTools())
                .extracting(tool -> tool.toolSpecification().name())
                .containsExactly("find_user");
        assertThat(scope.decisions()).anySatisfy(decision -> {
            assertThat(decision.subject()).isEqualTo("create_user");
            assertThat(decision.reasonCode()).isEqualTo("TOOL_PERMISSION_DENIED");
        });
    }
    
    @Test
    void shouldHideToolsetWhenEnvironmentOrDependencyDoesNotMatch() {
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of("BUSINESS"), Set.of("USER_ADMIN"), Set.of("prod"),
                Set.of("iam-api"), Set.of(), false);
        ToolsetContribution contribution = ToolsetContribution.fromBeans(descriptor, List.of(new UserTools()),
                List.of(governance("find_user", "*"), governance("create_user", "*")));
        ToolsetResolver resolver = new ToolsetResolver(List.of(context -> List.of(contribution)),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        ToolsetSelectionContext context = new ToolsetSelectionContext("1", "2", "conversation-1", "turn-1",
                "BUSINESS", "USER_ADMIN", "test", Set.of(), Set.of("module:iam:user-management"), Set.of());
        
        ResolvedCapabilityScope scope = resolver.resolve(context);
        
        assertThat(scope.selectedToolsets()).isEmpty();
        assertThat(scope.toolProvider().provideTools(null).aiServiceTools()).isEmpty();
        assertThat(scope.decisions()).anySatisfy(decision -> {
            assertThat(decision.subject()).isEqualTo("module:iam:user-management");
            assertThat(decision.reasonCode()).isEqualTo("ENVIRONMENT_MISMATCH");
        });
    }
    
    @Test
    void shouldRejectToolWithoutExplicitGovernance() {
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
        
        assertThatThrownBy(() -> ToolsetContribution.fromBeans(descriptor, List.of(new UserTools()),
                List.of(governance("find_user", "*"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("业务 Tool 缺少显式治理元数据")
                        .hasMessageContaining("create_user");
    }
    
    @Test
    void requirementProviderShouldUseTrustedInvocationContextAndExactToolRevision() {
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of("BUSINESS"), Set.of(), Set.of(), Set.of(), Set.of(),
                false);
        ToolsetContribution contribution = ToolsetContribution.fromBeans(descriptor, List.of(new UserTools()),
                List.of(governance("find_user", "sys:user:page"), governance("create_user", "sys:user:add")));
        ToolsetResolver resolver = new ToolsetResolver(List.of(context -> List.of(contribution)),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        ToolsetSelectionContext selection = new ToolsetSelectionContext("1", "2", "conversation-1", "turn-1",
                "BUSINESS", "QUERY", "prod", Set.of("sys:user:page", "sys:user:add"), Set.of(), Set.of());
        InvocationParameters parameters = InvocationParameters.from(Map.of(
                ToolsetSelectionContext.PARAMETER_KEY, selection));
        InvocationContext invocation = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1").invocationParameters(parameters).build();
        UserMessage message = UserMessage.from("查询用户");
        ToolProviderRequest request = ToolProviderRequest.builder().invocationContext(invocation)
                .userMessage(message).messages(List.of(message)).build();
        
        var result = resolver.providerForRequirements(Set.of("module:iam:user-management"),
                Set.of("find_user@1.0.0"), mock(HarnessOperationCoordinator.class)).provideTools(request);
        
        assertThat(result.aiServiceTools()).extracting(tool -> tool.toolSpecification().name())
                .containsExactly("find_user");
        InvocationContext untrusted = InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1").build();
        assertThat(resolver.providerForRequirements(Set.of("module:iam:user-management"), Set.of(),
                mock(HarnessOperationCoordinator.class))
                .provideTools(ToolProviderRequest.builder().invocationContext(untrusted)
                        .userMessage(message).messages(List.of(message)).build())
                .aiServiceTools()).isEmpty();
    }
    
    @Test
    void shouldRejectToolNamesUnsupportedByMainstreamModelApis() {
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:iam:user-management", "1.0.0",
                ToolsetSource.MODULE, "IAM 用户管理", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
        
        assertThatThrownBy(() -> ToolsetContribution.fromBeans(descriptor, List.of(new InvalidNameTools()),
                List.of(governance("iam.resolve-org", "*"))))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("Tool 名称只能包含字母、数字、下划线或中划线")
                        .hasMessageContaining("iam.resolve-org");
    }
    
    @Test
    void resolvedScopeShouldKeepExactGovernanceAcrossTenantResolutions() {
        ToolsetContributor contributor = context -> {
            String toolsetId = "mcp:" + context.tenantId() + ":10";
            ToolsetDescriptor descriptor = new ToolsetDescriptor(toolsetId, "schema-" + context.tenantId(),
                    ToolsetSource.MCP, "租户 MCP", Set.of("BUSINESS"), Set.of(), Set.of(), Set.of(), Set.of(), true);
            ToolGovernance governance = new ToolGovernance("find_user", toolsetId, "mcp-10",
                    "schema-" + context.tenantId(), "tenant:" + context.tenantId(), HarnessRiskLevel.LOW,
                    false, true, false, false, null, null);
            return List.of(ToolsetContribution.fromBeans(descriptor, List.of(new UserTools()), List.of(
                    governance, new ToolGovernance("create_user", toolsetId, "mcp-10",
                            "schema-" + context.tenantId(), "tenant:" + context.tenantId(), HarnessRiskLevel.LOW,
                            false, true, false, false, null, null))));
        };
        ToolsetResolver resolver = new ToolsetResolver(List.of(contributor),
                List.of(new DefaultToolsetAvailabilityPolicy()));
        ToolsetSelectionContext tenantOne = selection("1", "mcp:1:10", "tenant:1");
        ToolsetSelectionContext tenantTwo = selection("2", "mcp:2:10", "tenant:2");
        
        ResolvedCapabilityScope first = resolver.resolve(tenantOne);
        resolver.resolve(tenantTwo);
        
        assertThat(first.resolveIdentity("mcp-10:find_user@schema-1"))
                .get().extracting(ToolGovernance::toolsetId).isEqualTo("mcp:1:10");
    }
    
    private static ToolsetSelectionContext selection(String tenantId, String toolsetId, String permission) {
        return new ToolsetSelectionContext(tenantId, "2", "conversation-1", "turn-1", "BUSINESS", "QUERY",
                "prod", Set.of(permission), Set.of(toolsetId), Set.of());
    }
    
    private static ToolGovernance governance(String name, String permission) {
        return new ToolGovernance(name, "module:iam:user-management", "iam", "1.0.0", permission,
                HarnessRiskLevel.LOW,
                false, true, false, false, null, null);
    }
    
    private static final class UserTools {
        
        @Tool(name = "find_user", value = "查询用户")
        public String findUser() {
            return "用户";
        }
        
        @Tool(name = "create_user", value = "创建用户")
        public String createUser() {
            return "已创建";
        }
    }
    
    private static final class InvalidNameTools {
        
        @Tool(name = "iam.resolve-org", value = "非法名称工具")
        public String resolveOrg() {
            return "组织";
        }
    }
}
