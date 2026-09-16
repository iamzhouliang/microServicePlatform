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

import com.microservice.platform.suite.feign.OssFileFeign;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI Skill OSS 存储 Feign 适配器")
class FeignSkillStorageServiceTest {

    @Test
    @DisplayName("技能文件夹操作委托给通用 OSS 文件能力")
    void delegatesSkillFolderOperationsToGeneralOssFileFeign() {
        OssFileFeign ossFileFeign = mock(OssFileFeign.class);
        final FeignSkillStorageService service = new FeignSkillStorageService(ossFileFeign);
        final MockMultipartFile skillFile = new MockMultipartFile("file", "SKILL.md", "text/markdown",
                "content".getBytes(StandardCharsets.UTF_8));
        when(ossFileFeign.readText("ai/skills/security-review/SKILL.md")).thenReturn("skill content");
        when(ossFileFeign.listObjects("ai/skills/security-review/")).thenReturn(List.of(
                "ai/skills/security-review/SKILL.md",
                "ai/skills/security-review/references/checklist.md"));
        when(ossFileFeign.downloadFolder("ai/skills/security-review/")).thenReturn("zip bytes".getBytes(StandardCharsets.UTF_8));

        service.upload("ai/skills/security-review/SKILL.md", skillFile);
        service.writeText("ai/skills/security-review/references/checklist.md", "updated checklist");
        final String content = service.readText("ai/skills/security-review/SKILL.md");
        final List<String> objects = service.list("ai/skills/security-review/");
        final byte[] zipBytes = service.downloadFolder("ai/skills/security-review/");
        service.deleteFolder("ai/skills/security-review/");

        verify(ossFileFeign).uploadObject("ai/skills/security-review/SKILL.md", skillFile);
        verify(ossFileFeign).writeText("ai/skills/security-review/references/checklist.md", "updated checklist");
        verify(ossFileFeign).readText("ai/skills/security-review/SKILL.md");
        verify(ossFileFeign).listObjects("ai/skills/security-review/");
        verify(ossFileFeign).downloadFolder("ai/skills/security-review/");
        verify(ossFileFeign).deleteFolder("ai/skills/security-review/");
        assertThat(content).isEqualTo("skill content");
        assertThat(objects).containsExactly(
                "ai/skills/security-review/SKILL.md",
                "ai/skills/security-review/references/checklist.md");
        assertThat(zipBytes).isEqualTo("zip bytes".getBytes(StandardCharsets.UTF_8));
    }
}
