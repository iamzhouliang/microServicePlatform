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

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.skills.Skill;
import dev.langchain4j.skills.SkillResource;
import java.util.List;
import java.util.Objects;

/**
 * 已发布 Skill 的不可变精确修订。模型只看到 code，审计使用 code@version+digest。
 *
 * @param code 模型可见的 Skill 编码
 * @param version 已发布版本
 * @param digest 正文完整性摘要
 * @param description Skill 简介
 * @param content Skill 正文
 * @param resources Skill 资源列表
 * @param toolProviders Skill 激活后可见的 ToolProvider 列表
 * @author xJh
 * @since 2026-07-18
 */
public record PublishedSkill(String code, String description, String version, String digest, String content,
        List<SkillResource> resources, List<ToolProvider> toolProviders) {

    public PublishedSkill {
        code = requireText(code, "技能代码");
        description = requireText(description, "技能描述");
        version = requireText(version, "技能版本");
        digest = requireText(digest, "技能摘要").toLowerCase();
        content = requireText(content, "技能正文");
        resources = List.copyOf(Objects.requireNonNull(resources, "技能资源不能为空"));
        toolProviders = List.copyOf(Objects.requireNonNull(toolProviders, "技能 ToolProvider 不能为空"));
        if (!digest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("技能摘要必须是 SHA-256 十六进制字符串");
        }
    }

    /** 转换为 LangChain4j 官方 Skill，不引入自定义激活协议。 */
    public Skill toOfficialSkill() {
        return Skill.builder().name(code).description(description).content(content)
                .resources(resources).toolProviders(toolProviders).build();
    }

    public String revisionIdentity() {
        return code + "@" + version + ":" + digest;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return value.trim();
    }
}
