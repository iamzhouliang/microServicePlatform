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

import com.microservice.platform.ai.service.WorkflowFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工作流文件服务实现
 * 使用内存 + 临时文件存储，适用于工作流执行时的临时文件
 *
 * @author xJh
 * @since 2026/02/05
 */
@Slf4j
@Service
public class WorkflowFileServiceImpl implements WorkflowFileService {

    /**
     * 文件元数据缓存
     * key: fileId, value: 文件元数据
     */
    private final Map<String, FileMetadata> fileMetadataCache = new ConcurrentHashMap<>();

    /**
     * 临时文件目录
     */
    private final Path tempDir;

    public WorkflowFileServiceImpl() {
        try {
            this.tempDir = Files.createTempDirectory("workflow-files-");
            log.info("Workflow file temp directory: {}", tempDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temp directory for workflow files", e);
        }
    }

    @Override
    public Map<String, Object> uploadFile(MultipartFile file) throws IOException {
        String fileId = generateFileId();
        String originalFilename = file.getOriginalFilename();
        String contentType = file.getContentType();
        long size = file.getSize();

        // 保存文件到临时目录
        Path filePath = tempDir.resolve(fileId);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // 保存元数据
        FileMetadata metadata = new FileMetadata(
                fileId,
                originalFilename,
                contentType,
                size,
                filePath.toString(),
                LocalDateTime.now());
        fileMetadataCache.put(fileId, metadata);

        log.debug("Uploaded workflow file: {} -> {}", originalFilename, fileId);

        return buildFileInfoMap(metadata);
    }

    @Override
    public List<Map<String, Object>> uploadFiles(List<MultipartFile> files) throws IOException {
        List<Map<String, Object>> results = new ArrayList<>();
        for (MultipartFile file : files) {
            results.add(uploadFile(file));
        }
        return results;
    }

    @Override
    public Map<String, Object> getFileInfo(String fileId) {
        FileMetadata metadata = fileMetadataCache.get(fileId);
        if (metadata == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }
        return buildFileInfoMap(metadata);
    }

    @Override
    public byte[] getFileContent(String fileId) {
        FileMetadata metadata = fileMetadataCache.get(fileId);
        if (metadata == null) {
            throw new IllegalArgumentException("File not found: " + fileId);
        }

        try {
            Path filePath = Path.of(metadata.filePath);
            return Files.readAllBytes(filePath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read file: " + fileId, e);
        }
    }

    @Override
    public void deleteFile(String fileId) {
        FileMetadata metadata = fileMetadataCache.remove(fileId);
        if (metadata != null) {
            try {
                Files.deleteIfExists(Path.of(metadata.filePath));
                log.debug("Deleted workflow file: {}", fileId);
            } catch (IOException e) {
                log.warn("Failed to delete file: {}", fileId, e);
            }
        }
    }

    /**
     * 生成文件ID
     * @return 处理结果
     */
    private String generateFileId() {
        return "wf_" + UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 构建文件信息 Map
     * @param metadata metadata 参数
     * @return 处理结果
     */
    private Map<String, Object> buildFileInfoMap(FileMetadata metadata) {
        Map<String, Object> info = new HashMap<>();
        info.put("fileId", metadata.fileId);
        info.put("name", metadata.originalFilename);
        info.put("size", metadata.size);
        info.put("contentType", metadata.contentType);
        info.put("uploadTime", metadata.uploadTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return info;
    }

    /**
     * 文件元数据
     * @param contentType contentType 参数
     * @param fileId fileId 参数
     * @param filePath filePath 参数
     * @param originalFilename originalFilename 参数
     * @param size size 参数
     * @param uploadTime uploadTime 参数
     */
    private record FileMetadata(
                                String fileId,
                                String originalFilename,
                                String contentType,
                                long size,
                                String filePath,
                                LocalDateTime uploadTime) {
    }
}
