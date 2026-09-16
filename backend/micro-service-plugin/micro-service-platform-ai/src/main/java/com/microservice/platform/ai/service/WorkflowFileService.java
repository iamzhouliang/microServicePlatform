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

package com.microservice.platform.ai.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 工作流文件服务接口
 *
 * @author xJh
 * @since 2026/02/05
 */
public interface WorkflowFileService {

    /**
     * 上传单个文件
     *
     * @param file 文件
     * @return 文件信息 {fileId, name, size, contentType, uploadTime}
     * @throws IOException 处理失败时抛出
     */
    Map<String, Object> uploadFile(MultipartFile file) throws IOException;

    /**
     * 批量上传文件
     *
     * @param files 文件列表
     * @return 文件信息列表
     * @throws IOException 处理失败时抛出
     */
    List<Map<String, Object>> uploadFiles(List<MultipartFile> files) throws IOException;

    /**
     * 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    Map<String, Object> getFileInfo(String fileId);

    /**
     * 获取文件内容
     *
     * @param fileId 文件ID
     * @return 文件字节数组
     */
    byte[] getFileContent(String fileId);

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     */
    void deleteFile(String fileId);
}
