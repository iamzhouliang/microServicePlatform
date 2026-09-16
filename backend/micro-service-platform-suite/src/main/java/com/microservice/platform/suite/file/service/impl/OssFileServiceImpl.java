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

package com.microservice.platform.suite.file.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Dict;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.core.type.TypeReference;
import com.microservice.framework.commons.JacksonUtils;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.suite.feign.domain.resp.OssFilePreviewResp;
import com.microservice.platform.suite.file.domain.dto.req.FileStoragePageReq;
import com.microservice.platform.suite.file.domain.dto.resp.OssFilePageResp;
import com.microservice.platform.suite.file.domain.entity.OssConfig;
import com.microservice.platform.suite.file.domain.entity.OssFile;
import com.microservice.platform.suite.file.domain.enums.MineType;
import com.microservice.platform.suite.file.event.OssConfigTemplate;
import com.microservice.platform.suite.file.repository.OssFileMapper;
import com.microservice.platform.suite.file.service.OssFileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.x.file.storage.core.FileInfo;
import org.dromara.x.file.storage.core.FileStorageService;
import org.dromara.x.file.storage.core.get.ListFilesResult;
import org.dromara.x.file.storage.core.get.RemoteDirInfo;
import org.dromara.x.file.storage.core.get.RemoteFileInfo;
import org.dromara.x.file.storage.core.hash.HashInfo;
import org.dromara.x.file.storage.core.platform.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * @author xiao1
 * @since 2024-12
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OssFileServiceImpl extends SuperServiceImpl<OssFileMapper, OssFile> implements OssFileService {

    private static final int MAX_LIST_FILES = 1000;

    private final FileStorageService fileStorageService;
    private final OssConfigTemplate ossConfigTemplate;

    @Override
    public OssFile upload(MultipartFile file) {
        String platform = defaultPlatform();
        FileInfo info = fileStorageService.of(file).setPlatform(platform).upload();
        OssFile ossFile = toFileInfoRecord(info);
        ossFile.setCategory(MineType.ofName(info.getContentType()));
        ossFile.setPlatform(platform);
        this.baseMapper.insert(ossFile);
        return ossFile;
    }

    @Override
    public void uploadObject(String objectPath, MultipartFile file) {
        String normalizedPath = normalizeObjectPath(objectPath);
        ObjectPath splitPath = splitObjectPath(normalizedPath);
        fileStorageService.of(file)
                .setPlatform(defaultPlatform())
                .setPath(splitPath.path())
                .setSaveFilename(splitPath.filename())
                .upload();
    }

    @Override
    public String readText(String objectPath) {
        ObjectPath splitPath = splitObjectPath(normalizeObjectPath(objectPath));
        RemoteFileInfo remoteFile = fileStorageService.getFile()
                .setPlatform(defaultPlatform())
                .setPath(splitPath.path())
                .setFilename(splitPath.filename())
                .getFile();
        if (remoteFile == null) {
            return null;
        }
        byte[] bytes = fileStorageService.download(remoteFile.toFileInfo()).bytes();
        return new String(bytes, StandardCharsets.UTF_8);
    }

    @Override
    public void writeText(String objectPath, String content) {
        String normalizedPath = normalizeObjectPath(objectPath);
        ObjectPath splitPath = splitObjectPath(normalizedPath);
        fileStorageService.of(new TextMultipartFile(splitPath.filename(), content))
                .setPlatform(defaultPlatform())
                .setPath(splitPath.path())
                .setSaveFilename(splitPath.filename())
                .upload();
    }

    @Override
    public Collection<String> listObjects(String folderPath) {
        String normalizedFolder = normalizeFolderPath(folderPath);
        return listFiles(defaultPlatform(), normalizedFolder)
                .stream()
                .map(this::toObjectPath)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    @Override
    public byte[] downloadFolder(String folderPath) {
        String platform = defaultPlatform();
        String normalizedFolder = normalizeFolderPath(folderPath);
        List<RemoteFileInfo> remoteFiles = listFiles(platform, normalizedFolder);
        if (CollUtil.isEmpty(remoteFiles)) {
            throw CheckedException.notFound("文件夹为空或不存在");
        }
        try (ByteArrayOutputStream out = new ByteArrayOutputStream();
             ZipOutputStream zip = new ZipOutputStream(out)) {
            for (RemoteFileInfo remoteFile : remoteFiles) {
                String objectPath = toObjectPath(remoteFile);
                if (!objectPath.startsWith(normalizedFolder)) {
                    continue;
                }
                String entryName = StrUtil.removePrefix(objectPath, normalizedFolder);
                if (StrUtil.isBlank(entryName)) {
                    continue;
                }
                zip.putNextEntry(new ZipEntry(entryName));
                zip.write(fileStorageService.download(remoteFile.toFileInfo()).bytes());
                zip.closeEntry();
            }
            zip.finish();
            return out.toByteArray();
        } catch (IOException ex) {
            throw CheckedException.badRequest("下载文件夹失败: " + ex.getMessage());
        }
    }

    @Override
    public void deleteFolder(String folderPath) {
        String normalizedFolder = normalizeFolderPath(folderPath);
        List<RemoteFileInfo> remoteFiles = listFiles(defaultPlatform(), normalizedFolder);
        for (RemoteFileInfo remoteFile : remoteFiles) {
            fileStorageService.delete(remoteFile.toFileInfo());
        }
    }

    @Override
    public OssFile uploadImage(MultipartFile file) {
        FileInfo info = fileStorageService.of(file)
                // .setThumbnailSuffix() //指定缩略图后缀，必须是 thumbnailator 支持的图片格式，默认使用全局的
                // .setSaveThFilename() //指定缩略图的保存文件名，注意此文件名不含后缀，默认自动生成
                // 将图片大小调整到 1000*1000
                .image(img -> img.size(1000, 1000))
                // 再生成一张 200*200 的缩略图
                .thumbnail(th -> th.size(200, 200))
                .upload();
        return toFileInfoRecord(info);
    }

    @Override
    public void delete(Long id) {
        OssFile storage = Optional.ofNullable(this.baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("文件不存在"));
        FileStorage fileStorage = fileStorageService.getFileStorage(storage.getPlatform());
        if (fileStorage == null) {
            throw CheckedException.badRequest("未找到对应的存储平台或对应平台未开启");
        }
        FileInfo fileInfo = toFileInfo(storage);
        if (fileStorageService.delete(fileInfo)) {
            this.baseMapper.deleteById(id);
        }
    }

    @Override
    public void rename(Long id, String originName) {
        this.baseMapper.updateById(OssFile.builder().id(id).originalFilename(originName).build());
    }

    @Override
    public IPage<OssFilePageResp> pageList(FileStoragePageReq req) {
        return this.baseMapper.selectPage(req.buildPage(), Wraps.<OssFile>lbQ()
                        .eq(OssFile::getCategory, req.getCategory())
                        .like(OssFile::getOriginalFilename, req.getOriginalFilename())
                        .like(OssFile::getCreateName, req.getCreateName()))
                .convert(x -> BeanUtil.toBean(x, OssFilePageResp.class));
    }

    @Override
    public Map.Entry<String, String> preview(String filePath) {
        return null;
    }

    @Override
    public Collection<String> previewList(Set<String> req) {
        return List.of();
    }

    @Override
    public Map<String, OssFilePreviewResp> previewMap(Set<String> pathList) {
        return Map.of();
    }

    private List<RemoteFileInfo> listFiles(String platform, String folderPath) {
        List<RemoteFileInfo> files = new ArrayList<>();
        listFiles(platform, folderPath, files);
        return files;
    }

    private void listFiles(String platform, String folderPath, List<RemoteFileInfo> files) {
        String marker = null;
        do {
            ListFilesResult result = fileStorageService.listFiles()
                    .setPlatform(platform)
                    .setPath(folderPath)
                    .setMaxFiles(MAX_LIST_FILES)
                    .setMarker(marker)
                    .listFiles();
            if (CollUtil.isNotEmpty(result.getFileList())) {
                files.addAll(result.getFileList());
            }
            if (CollUtil.isNotEmpty(result.getDirList())) {
                for (RemoteDirInfo dirInfo : result.getDirList()) {
                    listFiles(platform, normalizeFolderPath(dirInfo.getPath() + dirInfo.getName()), files);
                }
            }
            marker = result.getNextMarker();
            if (!Boolean.TRUE.equals(result.getIsTruncated())) {
                marker = null;
            }
        } while (StrUtil.isNotBlank(marker));
    }

    private String toObjectPath(RemoteFileInfo remoteFile) {
        return normalizeFolderPath(remoteFile.getPath()) + remoteFile.getFilename();
    }

    private String defaultPlatform() {
        OssConfig setting = ossConfigTemplate.getDefaultStorageSetting();
        if (setting == null || StrUtil.isBlank(setting.getPlatform())) {
            throw CheckedException.badRequest("未找到默认对象存储配置");
        }
        String platform = setting.getPlatform();
        if (fileStorageService.getFileStorage(platform) == null) {
            throw CheckedException.badRequest("未找到对应的存储平台，请检查配置");
        }
        return platform;
    }

    private static ObjectPath splitObjectPath(String objectPath) {
        int index = objectPath.lastIndexOf('/');
        if (index < 0 || index == objectPath.length() - 1) {
            throw CheckedException.badRequest("对象路径必须包含文件名");
        }
        return new ObjectPath(objectPath.substring(0, index + 1), objectPath.substring(index + 1));
    }

    private static String normalizeObjectPath(String objectPath) {
        String path = normalizePath(objectPath);
        if (path.endsWith("/")) {
            throw CheckedException.badRequest("对象路径必须指向文件");
        }
        return path;
    }

    private static String normalizeFolderPath(String folderPath) {
        return StrUtil.addSuffixIfNot(normalizePath(folderPath), "/");
    }

    private static String normalizePath(String rawPath) {
        String path = StrUtil.trimToNull(rawPath);
        if (StrUtil.isBlank(path)) {
            throw CheckedException.badRequest("对象路径不能为空");
        }
        path = path.replace('\\', '/');
        if (path.startsWith("/") || path.contains("//")) {
            throw CheckedException.badRequest("对象路径不能包含绝对路径或空路径片段");
        }
        List<String> segments = StrUtil.split(path, '/');
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> StrUtil.equals(segment, ".") || StrUtil.equals(segment, ".."))) {
            throw CheckedException.badRequest("对象路径不能包含路径穿越片段");
        }
        return String.join("/", segments);
    }

    /**
     * 将 FileInfo 转为 FileInfoRecord
     */
    public OssFile toFileInfoRecord(FileInfo info) {
        OssFile detail = BeanUtil.copyProperties(info, OssFile.class, "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");
        if (StrUtil.isBlank(detail.getThFilename())) {
            detail.setThFilename(null);
        }
        detail.setMetadata(JacksonUtils.toJson(info.getMetadata()));
        detail.setUserMetadata(JacksonUtils.toJson(info.getUserMetadata()));
        detail.setThMetadata(JacksonUtils.toJson(info.getThMetadata()));
        detail.setThUserMetadata(JacksonUtils.toJson(info.getThUserMetadata()));
        detail.setAttr(JacksonUtils.toJson(info.getAttr()));
        detail.setHashInfo(JacksonUtils.toJson(info.getHashInfo()));
        return detail;
    }

    /**
     * 将 FileInfoRecord 转为 FileInfo
     */
    public FileInfo toFileInfo(OssFile detail) {
        FileInfo info = BeanUtil.copyProperties(detail, FileInfo.class, "metadata", "userMetadata", "thMetadata", "thUserMetadata", "attr", "hashInfo");
        if (StrUtil.isBlank(info.getThFilename())) {
            info.setThFilename(null);
        }
        // 这里手动获取数据库中的 json 字符串 并转成 元数据，方便使用
        info.setMetadata(jsonToMetadata(detail.getMetadata()));
        info.setUserMetadata(jsonToMetadata(detail.getUserMetadata()));
        info.setThMetadata(jsonToMetadata(detail.getThMetadata()));
        info.setThUserMetadata(jsonToMetadata(detail.getThUserMetadata()));
        // 这里手动获取数据库中的 json 字符串 并转成 附加属性字典，方便使用
        info.setAttr(JacksonUtils.toBean(detail.getAttr(), Dict.class));
        // 这里手动获取数据库中的 json 字符串 并转成 哈希信息，方便使用
        info.setHashInfo(JacksonUtils.toBean(detail.getHashInfo(), HashInfo.class));
        return info;
    }

    /**
     * 将 json 字符串转换成元数据对象
     */
    public Map<String, String> jsonToMetadata(String json) {
        if (StrUtil.isBlank(json)) {
            return null;
        }
        return JacksonUtils.readValue(json.replaceAll("^\"|\"$", ""), new TypeReference<>() {
        });
    }

    private record ObjectPath(String path, String filename) {
    }

    private record TextMultipartFile(String filename, String content) implements MultipartFile {

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            return filename;
        }

        @Override
        public String getContentType() {
            return "text/plain";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return bytes().length;
        }

        @Override
        public byte[] getBytes() {
            return bytes();
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(bytes());
        }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), bytes());
        }

        private byte[] bytes() {
            return StrUtil.nullToDefault(content, "").getBytes(StandardCharsets.UTF_8);
        }
    }

}
