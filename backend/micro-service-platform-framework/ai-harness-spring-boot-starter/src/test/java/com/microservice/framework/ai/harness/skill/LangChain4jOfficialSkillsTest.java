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

package com.microservice.framework.ai.harness.skill;

import static org.assertj.core.api.Assertions.assertThat;

import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.invocation.InvocationContext;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.SkillResource;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LangChain4jOfficialSkillsTest {
    
    @Test
    void shouldKeepNormalChatAvailableWhenNoSkillIsPublished() {
        PublishedSkillResolver resolver = new PublishedSkillResolver() {
            
            @Override
            public List<PublishedSkill> resolveActive() {
                return List.of();
            }
            
            @Override
            public Optional<PublishedSkill> resolveExact(String code, String version, String digest) {
                return Optional.empty();
            }
        };
        OfficialSkillsRuntime runtime = new OfficialSkillsFactory(resolver, activation -> {
            throw new AssertionError("空技能运行时不应记录激活事件");
        }).create();
        
        ToolProviderResult result = runtime.toolProvider()
                .provideTools(request(List.of(UserMessage.from("你好"))));
        
        assertThat(runtime.formatAvailableSkills()).isEmpty();
        assertThat(result.aiServiceTools()).isEmpty();
    }
    
    @Test
    void shouldUseOfficialActivationAndRecordVersionedRevision() {
        ToolSpecification scopedTool = ToolSpecification.builder().name("provision_user")
                .description("创建用户并分配角色")
                .parameters(JsonObjectSchema.builder().build())
                .build();
        ToolProvider scopedProvider = request -> ToolProviderResult.builder()
                .add(scopedTool, (toolRequest, memoryId) -> "创建成功")
                .build();
        PublishedSkill revision = new PublishedSkill("iam-user-management", "IAM 用户管理", "2.1.0",
                "a".repeat(64), "# IAM 用户管理\n仅在用户明确要求时操作。", List.of(), List.of(scopedProvider));
        PublishedSkillResolver resolver = new PublishedSkillResolver() {
            
            @Override
            public List<PublishedSkill> resolveActive() {
                return List.of(revision);
            }
            
            @Override
            public Optional<PublishedSkill> resolveExact(String code, String version, String digest) {
                return Optional.of(revision).filter(value -> value.code().equals(code)
                        && value.version().equals(version) && value.digest().equals(digest));
            }
        };
        List<SkillActivation> activations = new ArrayList<>();
        OfficialSkillsFactory factory = new OfficialSkillsFactory(resolver, activations::add);
        
        ToolProvider provider = factory.create().toolProvider();
        ToolProviderResult initial = provider.provideTools(request(List.of(UserMessage.from("创建用户"))));
        
        assertThat(initial.aiServiceTools()).extracting(tool -> tool.name())
                .containsExactly("activate_skill");
        ToolExecutionRequest activationRequest = ToolExecutionRequest.builder().id("activate-1")
                .name("activate_skill").arguments("{\"skill_name\":\"iam-user-management\"}").build();
        ToolExecutionResult activation = initial.toolExecutorByName("activate_skill")
                .executeWithContext(activationRequest, context());
        ToolExecutionResultMessage activationMessage = ToolExecutionResultMessage.builder()
                .id(activationRequest.id()).toolName(activationRequest.name()).text(activation.resultText())
                .attributes(activation.attributes()).build();
        List<ChatMessage> restored = List.of(UserMessage.from("创建用户"), AiMessage.from(activationRequest),
                activationMessage);
        
        ToolProviderResult afterActivation = provider.provideTools(request(restored));
        
        assertThat(afterActivation.aiServiceTools()).extracting(tool -> tool.name())
                .contains("activate_skill", "provision_user");
        assertThat(activations).singleElement().satisfies(record -> {
            assertThat(record.skillCode()).isEqualTo("iam-user-management");
            assertThat(record.skillVersion()).isEqualTo("2.1.0");
            assertThat(record.contentDigest()).isEqualTo("a".repeat(64));
            assertThat(record.conversationId()).isEqualTo("conversation-1");
        });
    }
    
    @Test
    void shouldRestoreConversationWithPreviouslyActivatedExactRevision() {
        PublishedSkill oldRevision = skillWithTool("1.0.0", "b".repeat(64), "old_revision_tool");
        PublishedSkill currentRevision = skillWithTool("2.0.0", "c".repeat(64), "current_revision_tool");
        PublishedSkillResolver resolver = new PublishedSkillResolver() {
            
            @Override
            public List<PublishedSkill> resolveActive() {
                return List.of(currentRevision);
            }
            
            @Override
            public Optional<PublishedSkill> resolveExact(String code, String version, String digest) {
                return Optional.of(oldRevision).filter(value -> value.code().equals(code)
                        && value.version().equals(version) && value.digest().equals(digest));
            }
        };
        SkillActivation previousActivation = new SkillActivation("tenant-1", "user-1", "conversation-1",
                "turn-old", oldRevision.code(), oldRevision.version(), oldRevision.digest(), Instant.EPOCH);
        SkillActivationLedger ledger = new SkillActivationLedger() {
            
            @Override
            public void record(SkillActivation activation) {
                // 本用例只验证会话恢复，不会产生新的激活记录。
            }
            
            @Override
            public List<SkillActivation> findConversation(String tenantId, String userId, String conversationId) {
                return List.of(previousActivation);
            }
        };
        
        ToolProvider provider = new OfficialSkillsFactory(resolver, ledger).create(contextInvocation()).toolProvider();
        ToolProviderResult initial = provider.provideTools(request(List.of(UserMessage.from("继续处理"))));
        ToolExecutionRequest activationRequest = ToolExecutionRequest.builder().id("activate-old")
                .name("activate_skill").arguments("{\"skill_name\":\"iam-user-management\"}").build();
        ToolExecutionResult activation = initial.toolExecutorByName("activate_skill")
                .executeWithContext(activationRequest, context());
        ToolExecutionResultMessage activationMessage = ToolExecutionResultMessage.builder()
                .id(activationRequest.id()).toolName(activationRequest.name()).text(activation.resultText())
                .attributes(activation.attributes()).build();
        
        ToolProviderResult restored = provider.provideTools(request(List.of(UserMessage.from("继续处理"),
                AiMessage.from(activationRequest), activationMessage)));
        
        assertThat(restored.aiServiceTools()).extracting(tool -> tool.name())
                .contains("old_revision_tool")
                .doesNotContain("current_revision_tool");
    }
    
    @Test
    void shouldReadAConcreteSkillResourceThroughOfficialTool() {
        SkillResource resource = SkillResource.builder().relativePath("references/roles.md")
                .content("管理员角色只能通过独立审批授予。")
                .build();
        PublishedSkill revision = new PublishedSkill("iam-user-management", "IAM 用户管理", "1.0.0",
                "d".repeat(64), "# IAM 用户管理\n按需读取角色规则。", List.of(resource), List.of());
        PublishedSkillResolver resolver = new PublishedSkillResolver() {
            
            @Override
            public List<PublishedSkill> resolveActive() {
                return List.of(revision);
            }
            
            @Override
            public Optional<PublishedSkill> resolveExact(String code, String version, String digest) {
                return Optional.of(revision);
            }
        };
        ToolProviderResult tools = new OfficialSkillsFactory(resolver, activation -> {
        }).create().toolProvider().provideTools(request(List.of(UserMessage.from("查看角色规则"))));
        ToolExecutionRequest read = ToolExecutionRequest.builder().id("resource-1")
                .name("read_skill_resource")
                .arguments("{\"skill_name\":\"iam-user-management\","
                        + "\"relative_path\":\"references/roles.md\"}")
                .build();
        
        ToolExecutionResult result = tools.toolExecutorByName("read_skill_resource")
                .executeWithContext(read, context());
        
        assertThat(result.isError()).isFalse();
        assertThat(result.resultText()).isEqualTo("管理员角色只能通过独立审批授予。");
    }
    
    private static PublishedSkill skillWithTool(String version, String digest, String toolName) {
        ToolSpecification tool = ToolSpecification.builder().name(toolName).description("精确修订测试工具")
                .parameters(JsonObjectSchema.builder().build()).build();
        ToolProvider provider = request -> ToolProviderResult.builder()
                .add(tool, (toolRequest, memoryId) -> "执行成功").build();
        return new PublishedSkill("iam-user-management", "IAM 用户管理", version, digest,
                "# IAM 用户管理\n按已激活版本执行。", List.of(), List.of(provider));
    }
    
    private static ToolProviderRequest request(List<ChatMessage> messages) {
        UserMessage user = messages.stream().filter(UserMessage.class::isInstance)
                .map(UserMessage.class::cast).findFirst().orElse(UserMessage.from("继续"));
        return ToolProviderRequest.builder().invocationContext(context()).userMessage(user).messages(messages).build();
    }
    
    private static InvocationContext context() {
        HarnessInvocation invocation = contextInvocation();
        return InvocationContext.builder().interfaceName("Assistant").methodName("chat")
                .methodArguments(List.of()).chatMemoryId("conversation-1")
                .invocationParameters(InvocationParameters.from(HarnessInvocation.PARAMETER_KEY, invocation))
                .build();
    }
    
    private static HarnessInvocation contextInvocation() {
        return new HarnessInvocation("tenant-1", "user-1", "conversation-1",
                "operation-1", "turn-1", Set.of("ai:chat"));
    }
}
