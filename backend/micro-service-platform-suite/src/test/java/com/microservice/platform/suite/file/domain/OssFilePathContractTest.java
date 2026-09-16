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

package com.microservice.platform.suite.file.domain;

import com.microservice.platform.suite.file.domain.dto.resp.OssFilePageResp;
import com.microservice.platform.suite.file.domain.entity.OssFile;
import com.microservice.platform.suite.file.service.OssFileService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OSS 文件路径响应契约")
class OssFilePathContractTest {

    @Test
    @DisplayName("文件实体保留组件路径字段并额外暴露完整对象路径")
    void entityExposesObjectPathWithoutChangingStoragePathFields() {
        OssFile file = OssFile.builder()
                .basePath("dev/")
                .path("ai/skills/wiki/")
                .filename("SKILL.md")
                .build();

        assertThat(file.getPath()).isEqualTo("ai/skills/wiki/");
        assertThat(file.getObjectPath()).isEqualTo("ai/skills/wiki/SKILL.md");
        assertThat(file.getStoragePath()).isEqualTo("dev/ai/skills/wiki/SKILL.md");
    }

    @Test
    @DisplayName("分页响应在 path 为空时也返回可定位的对象路径")
    void pageResponseExposesObjectPathWhenPathIsBlank() {
        OssFilePageResp resp = new OssFilePageResp();
        resp.setBasePath("dev/");
        resp.setPath("");
        resp.setFilename("693ba23e28ccaff50c5a9971.png");

        assertThat(resp.getObjectPath()).isEqualTo("693ba23e28ccaff50c5a9971.png");
        assertThat(resp.getStoragePath()).isEqualTo("dev/693ba23e28ccaff50c5a9971.png");
    }

    @Test
    @DisplayName("完整存储路径不会重复拼接基础路径")
    void storagePathDoesNotDuplicateBasePath() {
        OssFile file = OssFile.builder()
                .basePath("dev/")
                .path("dev/ai/skills/wiki/")
                .filename("SKILL.md")
                .build();

        assertThat(file.getStoragePath()).isEqualTo("dev/ai/skills/wiki/SKILL.md");
    }

    @Test
    @DisplayName("基础 OSS 文件服务提供按对象路径写入文本对象的能力")
    void ossFileServiceProvidesTextObjectWriteCapability() throws Exception {
        assertThat(OssFileService.class.getMethod("writeText", String.class, String.class)).isNotNull();
        var method = com.microservice.platform.suite.file.controller.OssFileController.class
                .getMethod("writeText", String.class, String.class);
        PutMapping mapping = method.getAnnotation(PutMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("/objects/text");
        assertThat(mapping.consumes()).contains(MediaType.TEXT_PLAIN_VALUE);
        assertThat(method.getParameters()[0].getAnnotation(RequestParam.class).value()).isEqualTo("objectPath");
        RequestBody requestBody = method.getParameters()[1].getAnnotation(RequestBody.class);
        assertThat(requestBody).isNotNull();
        assertThat(requestBody.required()).isFalse();
    }
}
