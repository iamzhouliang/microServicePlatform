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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Agent Skill 正文及其 SHA-256 摘要。
 *
 * @param content Skill 正文
 * @param contentDigest Provider 声明的正文摘要
 * @author xJh
 * @since 2026-07
 */
public record AgentSkillContent(String content, String contentDigest) {

    public AgentSkillContent {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("技能正文不能为空");
        }
        if (contentDigest == null || !contentDigest.matches("(?i)[0-9a-f]{64}")) {
            throw new IllegalArgumentException("正文摘要必须是 64 位 SHA-256 十六进制字符串");
        }
        contentDigest = contentDigest.toLowerCase(Locale.ROOT);
    }

    public static AgentSkillContent of(String content) {
        return new AgentSkillContent(content, sha256(content));
    }

    public static String sha256(String content) {
        if (content == null) {
            throw new IllegalArgumentException("技能正文不能为空");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", failure);
        }
    }
}
