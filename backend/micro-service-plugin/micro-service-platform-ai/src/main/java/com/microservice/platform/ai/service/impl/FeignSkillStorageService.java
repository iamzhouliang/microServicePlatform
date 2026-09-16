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

import com.microservice.platform.ai.service.SkillStorageService;
import com.microservice.platform.suite.feign.OssFileFeign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 基于 suite OSS 服务的 AI 技能对象存储实现.
 *
 * @author xJh
 * @since 2026/06/24
 */
@Service
@RequiredArgsConstructor
public class FeignSkillStorageService implements SkillStorageService {

    private final OssFileFeign ossFileFeign;

    @Override
    public void upload(String objectPath, MultipartFile file) {
        ossFileFeign.uploadObject(objectPath, file);
    }

    @Override
    public String readText(String objectPath) {
        return ossFileFeign.readText(objectPath);
    }

    @Override
    public void writeText(String objectPath, String content) {
        ossFileFeign.writeText(objectPath, content);
    }

    @Override
    public List<String> list(String folderPath) {
        return ossFileFeign.listObjects(folderPath);
    }

    @Override
    public byte[] downloadFolder(String folderPath) {
        return ossFileFeign.downloadFolder(folderPath);
    }

    @Override
    public void deleteFolder(String folderPath) {
        ossFileFeign.deleteFolder(folderPath);
    }
}
