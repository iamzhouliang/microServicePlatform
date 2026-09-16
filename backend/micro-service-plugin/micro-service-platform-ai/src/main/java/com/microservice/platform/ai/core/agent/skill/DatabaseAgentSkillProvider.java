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

import cn.hutool.core.util.StrUtil;
import com.microservice.framework.ai.harness.skill.AgentSkillContent;
import com.microservice.framework.ai.harness.skill.PublishedSkill;
import com.microservice.framework.ai.harness.skill.PublishedSkillResolver;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.entity.AiSkill;
import com.microservice.platform.ai.repository.SkillMapper;
import com.microservice.platform.ai.service.SkillStorageService;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.SkillResource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 从数据库和对象存储解析 LangChain4j 官方 Skill 精确修订。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Component
@RequiredArgsConstructor
public class DatabaseAgentSkillProvider implements PublishedSkillResolver {

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final SkillMapper skillMapper;
    private final SkillStorageService skillStorageService;
    private final ToolsetResolver toolsetResolver;
    private final HarnessOperationCoordinator operationCoordinator;

    @Override
    public List<PublishedSkill> resolveActive() {
        List<AiSkill> rows = skillMapper.selectList(Wraps.<AiSkill>lbQ()
                .eq(AiSkill::getStatus, true)
                .eq(AiSkill::getPublished, true)
                .orderByAsc(AiSkill::getCode));
        Map<String, AiSkill> activeByCode = new LinkedHashMap<>();
        rows.forEach(skill -> {
            AiSkill previous = activeByCode.putIfAbsent(skill.getCode(), skill);
            if (previous != null) {
                throw new IllegalStateException("同一技能代码存在多个已发布修订：" + skill.getCode());
            }
        });
        return activeByCode.values().stream().map(this::toPublishedSkill).toList();
    }

    @Override
    public Optional<PublishedSkill> resolveExact(String code, String version, String digest) {
        AiSkill skill = skillMapper.selectOne(Wraps.<AiSkill>lbQ()
                .eq(AiSkill::getCode, requireText(code, "技能代码"))
                .eq(AiSkill::getVersion, requireText(version, "技能版本"))
                .eq(AiSkill::getContentDigest, requireDigest(digest)));
        return Optional.ofNullable(skill).map(this::toPublishedSkill);
    }

    private PublishedSkill toPublishedSkill(AiSkill skill) {
        String content = skillStorageService.readText(normalizeStoragePath(skill.getSkillFile(), "SKILL.md 路径"));
        String digest = requireDigest(skill.getContentDigest());
        if (!digest.equals(AgentSkillContent.sha256(content))) {
            throw new IllegalStateException("技能正文与已发布摘要不一致："
                    + skill.getCode() + "@" + skill.getVersion());
        }
        List<SkillResource> resources = loadResources(skill);
        List<ToolProvider> toolProviders = skill.getRequiresTools() == null || skill.getRequiresTools().isEmpty()
                ? List.of()
                : List.of(toolsetResolver.providerForRequirements(skill.getRequiresToolsets(),
                        skill.getRequiresTools(), operationCoordinator));
        return new PublishedSkill(skill.getCode(), skill.getDescription(), skill.getVersion(), digest, content,
                resources, toolProviders);
    }

    private List<SkillResource> loadResources(AiSkill skill) {
        String folder = normalizeFolder(skill.getSkillPath());
        return skillStorageService.list(folder).stream()
                .map(path -> normalizeStoragePath(path, "技能资源路径"))
                .filter(path -> path.startsWith(folder))
                .map(path -> normalizeRelativePath(StrUtil.removePrefix(path, folder)))
                .filter(StrUtil::isNotBlank)
                .filter(path -> !SKILL_FILE_NAME.equals(path))
                .sorted()
                .map(path -> (SkillResource) SkillResource.builder().relativePath(path)
                        .content(skillStorageService.readText(folder + path)).build())
                .toList();
    }

    private static String requireDigest(String digest) {
        String normalized = requireText(digest, "技能摘要").toLowerCase();
        if (!normalized.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("技能摘要格式无效");
        }
        return normalized;
    }

    private static String normalizeFolder(String value) {
        String normalized = normalizeStoragePath(value, "技能目录");
        return normalized.endsWith("/") ? normalized : normalized + "/";
    }

    private static String normalizeStoragePath(String value, String field) {
        String normalized = requireText(value, field).replace('\\', '/');
        if (normalized.startsWith("/") || normalized.matches("^[a-zA-Z]:/.*")) {
            throw new IllegalArgumentException(field + "必须是对象存储相对路径");
        }
        String[] segments = normalized.split("/");
        for (String segment : segments) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(field + "包含非法路径片段");
            }
        }
        return String.join("/", segments);
    }

    private static String normalizeRelativePath(String value) {
        return normalizeStoragePath(value, "Skill 资源相对路径");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
