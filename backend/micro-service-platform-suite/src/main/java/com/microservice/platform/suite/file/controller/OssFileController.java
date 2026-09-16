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

package com.microservice.platform.suite.file.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.suite.feign.domain.resp.OssFilePreviewResp;
import com.microservice.platform.suite.file.domain.dto.req.FileStoragePageReq;
import com.microservice.platform.suite.file.domain.dto.resp.OssFilePageResp;
import com.microservice.platform.suite.file.domain.entity.OssFile;
import com.microservice.platform.suite.file.service.OssFileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OSS 文件管理控制器
 * <p>
 * 提供文件上传、下载、预览、删除等功能
 *
 * @author Levin
 * @since 2024-12
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/oss/files")
@RequiredArgsConstructor
@Tag(name = "文件管理", description = "OSS 文件上传、下载、预览管理")
public class OssFileController {

    private final OssFileService ossFileService;

    /**
     * 分页查询文件列表
     */
    @GetMapping
    @Operation(summary = "分页查询", description = "分页查询文件列表")
    public IPage<OssFilePageResp> page(FileStoragePageReq req) {
        return ossFileService.pageList(req);
    }

    /**
     * 上传文件
     */
    @PostMapping("/upload")
    @Operation(summary = "上传文件", description = "上传任意类型文件")
    public OssFile upload(@RequestParam("file") MultipartFile file) {
        return ossFileService.upload(file);
    }

    /**
     * 按对象路径上传文件
     */
    @PostMapping("/objects/upload")
    @Operation(summary = "按对象路径上传文件", description = "上传文件到指定对象路径")
    public void uploadObject(@RequestParam("objectPath") String objectPath, @RequestPart("file") MultipartFile file) {
        ossFileService.uploadObject(objectPath, file);
    }

    /**
     * 按对象路径读取文本文件
     */
    @GetMapping("/objects/text")
    @Operation(summary = "读取文本对象", description = "根据对象路径读取文本文件内容")
    public String readText(@RequestParam("objectPath") String objectPath) {
        return ossFileService.readText(objectPath);
    }

    /**
     * 按对象路径写入文本文件
     */
    @PutMapping(value = "/objects/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "写入文本对象", description = "根据对象路径写入文本文件内容")
    public void writeText(@RequestParam("objectPath") String objectPath, @RequestBody(required = false) String content) {
        ossFileService.writeText(objectPath, content);
    }

    /**
     * 按文件夹前缀列出对象
     */
    @GetMapping("/objects")
    @Operation(summary = "列出文件夹对象", description = "根据文件夹前缀列出对象路径")
    public List<String> listObjects(@RequestParam("folderPath") String folderPath) {
        return ossFileService.listObjects(folderPath).stream().toList();
    }

    /**
     * 按文件夹前缀下载对象
     */
    @GetMapping(value = "/folders/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(summary = "下载文件夹对象", description = "根据文件夹前缀将对象打包为 zip 下载")
    public byte[] downloadFolder(@RequestParam("folderPath") String folderPath) {
        return ossFileService.downloadFolder(folderPath);
    }

    /**
     * 按文件夹前缀删除对象
     */
    @DeleteMapping("/folders")
    @Operation(summary = "删除文件夹对象", description = "根据文件夹前缀删除对象")
    public void deleteFolder(@RequestParam("folderPath") String folderPath) {
        ossFileService.deleteFolder(folderPath);
    }

    /**
     * 上传图片
     */
    @PostMapping("/upload/image")
    @Operation(summary = "上传图片", description = "上传图片文件（支持压缩、缩略图）")
    public OssFile uploadImage(@RequestParam("file") MultipartFile file) {
        return ossFileService.uploadImage(file);
    }

    /**
     * 获取文件预览地址
     */
    @GetMapping("/preview")
    @Operation(summary = "文件预览", description = "根据文件路径获取临时访问地址")
    public Map.Entry<String, String> preview(@RequestParam String path) {
        return ossFileService.preview(path);
    }

    /**
     * 批量获取预览地址（返回列表）
     */
    @PostMapping("/preview/batch")
    @Operation(summary = "批量预览", description = "批量获取文件预览地址")
    public Collection<String> previewBatch(@RequestBody Set<String> paths) {
        return ossFileService.previewList(paths);
    }

    /**
     * 批量获取预览地址（返回 Map）
     */
    @PostMapping("/preview/batch-map")
    @Operation(summary = "批量预览(Map)", description = "批量获取文件预览地址，返回 path -> url 映射")
    public Map<String, OssFilePreviewResp> previewBatchMap(@RequestBody Set<String> paths) {
        return ossFileService.previewMap(paths);
    }

    /**
     * 重命名文件
     */
    @PutMapping("/{id}/name")
    @AccessLog(module = "文件管理", description = "文件重命名")
    @Operation(summary = "重命名", description = "修改文件名称")
    public void rename(@PathVariable Long id, @RequestParam String name) {
        ossFileService.rename(id, name);
    }

    /**
     * 删除文件
     */
    @DeleteMapping("/{id}")
    @AccessLog(module = "文件管理", description = "删除文件")
    @Operation(summary = "删除文件", description = "删除指定文件")
    public void delete(@PathVariable Long id) {
        ossFileService.delete(id);
    }
}
