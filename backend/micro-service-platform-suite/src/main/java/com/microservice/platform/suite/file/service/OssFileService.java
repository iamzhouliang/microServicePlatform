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

package com.microservice.platform.suite.file.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.suite.feign.domain.resp.OssFilePreviewResp;
import com.microservice.platform.suite.file.domain.dto.req.FileStoragePageReq;
import com.microservice.platform.suite.file.domain.dto.resp.OssFilePageResp;
import com.microservice.platform.suite.file.domain.entity.OssFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * OSS 文件服务
 * <p>
 * 提供文件上传、下载、预览、删除等功能
 *
 * @author Levin
 * @since 2024-12
 */
public interface OssFileService extends SuperService<OssFile> {

    /**
     * 分页查询文件列表
     *
     * @param req 查询条件
     * @return 分页结果
     */
    IPage<OssFilePageResp> pageList(FileStoragePageReq req);

    /**
     * 上传文件
     *
     * @param file 文件
     * @return 文件信息
     */
    OssFile upload(MultipartFile file);

    /**
     * 按对象路径上传文件.
     *
     * @param objectPath 对象路径
     * @param file 文件
     */
    void uploadObject(String objectPath, MultipartFile file);

    /**
     * 按对象路径读取文本文件.
     *
     * @param objectPath 对象路径
     * @return 文本内容
     */
    String readText(String objectPath);

    /**
     * 按对象路径写入文本文件.
     *
     * @param objectPath 对象路径
     * @param content 文本内容
     */
    void writeText(String objectPath, String content);

    /**
     * 按文件夹前缀列出对象.
     *
     * @param folderPath 文件夹前缀
     * @return 对象路径列表
     */
    Collection<String> listObjects(String folderPath);

    /**
     * 按文件夹前缀下载对象为 zip 包.
     *
     * @param folderPath 文件夹前缀
     * @return zip 文件字节
     */
    byte[] downloadFolder(String folderPath);

    /**
     * 按文件夹前缀删除对象.
     *
     * @param folderPath 文件夹前缀
     */
    void deleteFolder(String folderPath);

    /**
     * 上传图片
     * <p>
     * 支持图片压缩、缩略图生成等
     *
     * @param file 图片文件
     * @return 文件信息
     */
    OssFile uploadImage(MultipartFile file);

    /**
     * 获取文件预览地址
     * <p>
     * 生成带临时访问 Token 的预览 URL
     *
     * @param filePath 文件路径
     * @return 预览地址 (key: 文件路径, value: 预览URL)
     */
    Map.Entry<String, String> preview(String filePath);

    /**
     * 批量获取预览地址
     *
     * @param paths 文件路径集合
     * @return 预览地址列表
     */
    Collection<String> previewList(Set<String> paths);

    /**
     * 批量获取预览地址（返回 Map）
     *
     * @param paths 文件路径集合
     * @return 文件路径 -> 预览信息 映射
     */
    Map<String, OssFilePreviewResp> previewMap(Set<String> paths);

    /**
     * 重命名文件
     *
     * @param id   文件 ID
     * @param name 新文件名
     */
    void rename(Long id, String name);

    /**
     * 删除文件
     *
     * @param id 文件 ID
     */
    void delete(Long id);
}
