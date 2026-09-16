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

package com.microservice.platform.ai.core.agent.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.skill.PublishedSkill;
import com.microservice.platform.ai.domain.entity.AiSkill;
import com.microservice.platform.ai.repository.SkillMapper;
import com.microservice.platform.ai.service.SkillStorageService;
import dev.langchain4j.service.tool.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class DatabaseAgentSkillProviderTest {

    private static final String CONTENT = "---\nname: iam-user-management\ndescription: IAM 用户管理\n---\n# 用户管理";
    private static final String DIGEST = digest(CONTENT);

    @Test
    void shouldResolveDatabaseRevisionAsOfficialSkill() {
        SkillMapper mapper = mock(SkillMapper.class);
        SkillStorageService storage = mock(SkillStorageService.class);
        final ToolsetResolver resolver = mock(ToolsetResolver.class);
        final HarnessOperationCoordinator coordinator = mock(HarnessOperationCoordinator.class);
        ToolProvider scopedTools = mock(ToolProvider.class);
        AiSkill skill = skill("1.0.0", true);
        when(mapper.selectList(any())).thenReturn(List.of(skill));
        when(storage.readText(skill.getSkillFile())).thenReturn(CONTENT);
        when(storage.list(skill.getSkillPath())).thenReturn(List.of(skill.getSkillFile()));
        when(resolver.providerForRequirements(skill.getRequiresToolsets(), skill.getRequiresTools(), coordinator))
                .thenReturn(scopedTools);

        PublishedSkill published = new DatabaseAgentSkillProvider(mapper, storage, resolver, coordinator)
                .resolveActive().getFirst();

        assertThat(published.code()).isEqualTo("iam-user-management");
        assertThat(published.version()).isEqualTo("1.0.0");
        assertThat(published.digest()).isEqualTo(DIGEST);
        assertThat(published.toolProviders()).containsExactly(scopedTools);
        assertThat(published.toOfficialSkill().name()).isEqualTo("iam-user-management");
    }

    @Test
    void shouldRejectMultipleActiveRevisionsForSameCode() {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of(skill("1.0.0", true), skill("2.0.0", true)));

        assertThatThrownBy(() -> new DatabaseAgentSkillProvider(mapper, mock(SkillStorageService.class),
                mock(ToolsetResolver.class), mock(HarnessOperationCoordinator.class)).resolveActive())
                        .hasMessageContaining("多个已发布修订");
    }

    @Test
    void shouldRestoreExactDelistedRevisionByDigest() {
        SkillMapper mapper = mock(SkillMapper.class);
        SkillStorageService storage = mock(SkillStorageService.class);
        final ToolsetResolver resolver = mock(ToolsetResolver.class);
        final HarnessOperationCoordinator coordinator = mock(HarnessOperationCoordinator.class);
        AiSkill skill = skill("1.0.0", false);
        when(mapper.selectOne(any())).thenReturn(skill);
        when(storage.readText(skill.getSkillFile())).thenReturn(CONTENT);
        when(storage.list(skill.getSkillPath())).thenReturn(List.of(skill.getSkillFile()));
        when(resolver.providerForRequirements(skill.getRequiresToolsets(), skill.getRequiresTools(), coordinator))
                .thenReturn(mock(ToolProvider.class));

        assertThat(new DatabaseAgentSkillProvider(mapper, storage, resolver, coordinator)
                .resolveExact(skill.getCode(), skill.getVersion(), DIGEST))
                        .get().extracting(PublishedSkill::revisionIdentity)
                        .isEqualTo("iam-user-management@1.0.0:" + DIGEST);
    }

    @Test
    void shouldVerifyDigestWithoutTrimmingSkillContent() {
        String contentWithTrailingLineBreak = CONTENT + "\n";
        SkillMapper mapper = mock(SkillMapper.class);
        SkillStorageService storage = mock(SkillStorageService.class);
        final ToolsetResolver resolver = mock(ToolsetResolver.class);
        final HarnessOperationCoordinator coordinator = mock(HarnessOperationCoordinator.class);
        AiSkill skill = skill("1.0.0", true);
        skill.setContentDigest(digest(contentWithTrailingLineBreak));
        when(mapper.selectList(any())).thenReturn(List.of(skill));
        when(storage.readText(skill.getSkillFile())).thenReturn(contentWithTrailingLineBreak);
        when(storage.list(skill.getSkillPath())).thenReturn(List.of(skill.getSkillFile()));
        when(resolver.providerForRequirements(skill.getRequiresToolsets(), skill.getRequiresTools(), coordinator))
                .thenReturn(mock(ToolProvider.class));

        assertThat(new DatabaseAgentSkillProvider(mapper, storage, resolver, coordinator).resolveActive()).hasSize(1);
    }

    private static AiSkill skill(String version, boolean published) {
        return AiSkill.builder().id(10L).code("iam-user-management").name("IAM 用户管理")
                .description("创建和查询 IAM 用户").version(version)
                .requiresToolsets(List.of("module:iam:user-management"))
                .requiresTools(List.of("iam_provision_user@1.0.0", "iam_search_user@1.0.0"))
                .skillPath("ai/skills/iam-user-management/1.0.0/")
                .skillFile("ai/skills/iam-user-management/1.0.0/SKILL.md")
                .contentDigest(DIGEST).status(true).published(published).build();
    }

    private static String digest(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }
}
