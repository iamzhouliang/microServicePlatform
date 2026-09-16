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

package com.microservice.platform.ai.controller;

import com.microservice.platform.ai.service.WorkflowFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 工作流文件上传控制器
 * 处理工作流执行时的文件上传需求
 *
 * @author xJh
 * @since 2026/02/05
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/workflow-files")
@Tag(name = "工作流文件管理", description = "工作流执行文件上传和管理")
public class WorkflowFileController {

    private final WorkflowFileService workflowFileService;

    /**
     * 上传单个文件
     *
     * @param file 文件
     * @return 文件信息（包含 fileId）
     * @throws IOException 处理失败时抛出
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传文件并返回文件ID，用于工作流执行")
    public Map<String, Object> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return workflowFileService.uploadFile(file);
    }

    /**
     * 批量上传文件
     *
     * @param files 文件列表
     * @return 文件信息列表
     * @throws IOException 处理失败时抛出
     */
    @PostMapping("/upload-batch")
    @Operation(summary = "批量上传文件")
    public List<Map<String, Object>> uploadBatch(@RequestParam("files") List<MultipartFile> files) throws IOException {
        return workflowFileService.uploadFiles(files);
    }

    /**
     * 获取文件内容
     *
     * @param fileId 文件ID
     * @return 文件内容（Base64 编码）
     */
    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件信息")
    public Map<String, Object> getFile(@PathVariable String fileId) {
        return workflowFileService.getFileInfo(fileId);
    }

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     */
    @DeleteMapping("/{fileId}")
    @Operation(summary = "删除文件")
    public void deleteFile(@PathVariable String fileId) {
        workflowFileService.deleteFile(fileId);
    }
}
