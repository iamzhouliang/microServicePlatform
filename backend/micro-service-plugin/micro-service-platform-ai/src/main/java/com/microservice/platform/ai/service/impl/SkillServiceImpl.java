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

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.config.SkillProperties;
import com.microservice.framework.ai.harness.skill.AgentSkillContent;
import com.microservice.framework.ai.harness.tool.AgentToolIdentity;
import com.microservice.platform.ai.domain.dto.req.SkillPageReq;
import com.microservice.platform.ai.domain.dto.req.SkillSaveReq;
import com.microservice.platform.ai.domain.dto.resp.SkillDetailResp;
import com.microservice.platform.ai.domain.dto.resp.SkillPageResp;
import com.microservice.platform.ai.domain.entity.AiSkill;
import com.microservice.platform.ai.repository.SkillMapper;
import com.microservice.platform.ai.service.SkillService;
import com.microservice.platform.ai.service.SkillStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * AI技能服务实现.
 *
 * @author xJh
 * @since 2026/06/24
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillServiceImpl extends SuperServiceImpl<SkillMapper, AiSkill> implements SkillService {

    private static final Pattern SKILL_CODE_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,99}$");

    private static final Pattern SKILL_VERSION_PATTERN = Pattern.compile("^[0-9A-Za-z][0-9A-Za-z._-]{0,49}$");

    private static final Pattern TOOLSET_ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9:_-]{0,199}$");

    private static final Pattern FRONT_MATTER_PATTERN = Pattern.compile("\\A---\\R(?<yaml>.*?)\\R---\\R?", Pattern.DOTALL);

    private static final String SKILL_FILE_NAME = "SKILL.md";

    private final SkillProperties skillProperties;
    private final SkillStorageService skillStorageService;

    @Override
    public IPage<SkillPageResp> pageList(SkillPageReq req) {
        return this.baseMapper.selectPage(req.buildPage(), Wraps.<AiSkill>lbQ()
                .like(AiSkill::getName, req.getName())
                .eq(AiSkill::getCode, req.getCode())
                .eq(AiSkill::getCategory, req.getCategory())
                .eq(AiSkill::getStatus, req.getStatus())
                .eq(AiSkill::getPublished, req.getPublished())
                .orderByDesc(AiSkill::getLastModifyTime))
                .convert(this::toPageResp);
    }

    @Override
    public SkillDetailResp detail(Long id) {
        return toDetailResp(getRequired(id), true);
    }

    @Override
    public SkillDetailResp preview(Long id) {
        return toDetailResp(getRequired(id), true);
    }

    @Override
    public byte[] download(Long id) {
        AiSkill skill = getRequired(id);
        String folder = StrUtil.blankToDefault(skill.getSkillPath(), skillFolder(skill.getCode()));
        return skillStorageService.downloadFolder(folder);
    }

    @Override
    public void updateFile(Long id, String path, String content) {
        AiSkill skill = getRequired(id);
        if (Boolean.TRUE.equals(skill.getPublished())) {
            throw CheckedException.badRequest("已发布技能不能在线修改文件，请创建新版本");
        }
        String relativePath = normalizeSkillFilePath(path);
        if (SKILL_FILE_NAME.equals(relativePath)) {
            validateFrontMatter(content);
            skillStorageService.writeText(resolveSkillObjectPath(skill, relativePath), content);
            skill.setContentDigest(AgentSkillContent.sha256(content));
            this.baseMapper.updateById(skill);
            return;
        } else if (!listResources(skill).contains(relativePath)) {
            throw CheckedException.notFound("技能目录文件不存在");
        }
        skillStorageService.writeText(resolveSkillObjectPath(skill, relativePath), StrUtil.nullToDefault(content, ""));
    }

    @Override
    public List<SkillDetailResp> listEnabled() {
        return this.baseMapper.selectList(Wraps.<AiSkill>lbQ()
                .eq(AiSkill::getStatus, true)
                .eq(AiSkill::getPublished, true)
                .orderByDesc(AiSkill::getLastModifyTime))
                .stream()
                .map(skill -> toDetailResp(skill, false))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(SkillSaveReq req, List<MultipartFile> files, List<String> relativePaths) {
        String code = normalizeSkillCode(req.getCode());
        String version = normalizeSkillVersion(req.getVersion());
        List<String> requiresTools = normalizeRequiredTools(req.getRequiresTools());
        List<String> requiresToolsets = normalizeRequiredToolsets(req.getRequiresToolsets());
        ensureUniqueSkill(code, version, null);
        String targetFolder = skillRevisionFolder(code, version);
        ensureSkillDirectoryAvailable(targetFolder);
        SkillUploadPackage uploadPackage = prepareUploadedSkillDirectory(files, relativePaths);
        try {
            uploadSkillDirectory(uploadPackage, targetFolder);
            AiSkill skill = BeanUtil.toBean(req, AiSkill.class);
            fillSkillPackageFields(skill, code, version, requiresTools, requiresToolsets,
                    uploadPackage.contentDigest(), targetFolder);
            skill.setStatus(true);
            skill.setPublished(false);
            this.baseMapper.insert(skill);
            log.info("创建AI技能成功, id={}, code={}, skillFile={}", skill.getId(), skill.getCode(), skill.getSkillFile());
        } catch (RuntimeException ex) {
            deleteSkillDirectory(targetFolder);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, SkillSaveReq req, List<MultipartFile> files, List<String> relativePaths) {
        AiSkill existing = getRequired(id);
        String code = normalizeSkillCode(req.getCode());
        String version = normalizeSkillVersion(req.getVersion());
        List<String> requiresTools = normalizeRequiredTools(req.getRequiresTools());
        List<String> requiresToolsets = normalizeRequiredToolsets(req.getRequiresToolsets());
        ensureUniqueSkill(code, version, id);
        boolean codeChanged = !StrUtil.equals(existing.getCode(), code);
        boolean versionChanged = !StrUtil.equals(existing.getVersion(), version);
        boolean identityChanged = codeChanged || versionChanged;
        boolean published = Boolean.TRUE.equals(existing.getPublished());
        if (published && !identityChanged) {
            throw CheckedException.badRequest("已发布技能不能修改原版本，请创建新版本");
        }
        String targetFolder = skillRevisionFolder(code, version);
        ensureSkillDirectoryAvailable(targetFolder);
        SkillUploadPackage uploadPackage = prepareUploadedSkillDirectory(files, relativePaths);
        boolean insertNewVersion = published && identityChanged;

        try {
            uploadSkillDirectory(uploadPackage, targetFolder);
            AiSkill skill = insertNewVersion
                    ? BeanUtil.toBean(req, AiSkill.class)
                    : BeanUtilPlus.toBean(id, req, AiSkill.class);
            skill.setId(insertNewVersion ? null : id);
            fillSkillPackageFields(skill, code, version, requiresTools, requiresToolsets,
                    uploadPackage.contentDigest(), targetFolder);
            skill.setStatus(existing.getStatus());
            skill.setPublished(existing.getPublished());
            if (insertNewVersion) {
                this.baseMapper.insert(skill);
            } else {
                this.baseMapper.updateById(skill);
                cleanupSkillFolderAfterCommit(existing, targetFolder);
            }
            log.info("修改AI技能成功, id={}, code={}, version={}, skillFile={}",
                    skill.getId(), skill.getCode(), skill.getVersion(), skill.getSkillFile());
        } catch (RuntimeException ex) {
            deleteSkillDirectory(targetFolder);
            throw ex;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        AiSkill skill = getRequired(id);
        this.baseMapper.deleteById(id);
        cleanupSkillFolderAfterCommit(skill, null);
        log.info("删除AI技能成功, id={}, code={}", id, skill.getCode());
    }

    @Override
    public void toggleStatus(Long id, Boolean status) {
        AiSkill skill = getRequired(id);
        skill.setStatus(Boolean.TRUE.equals(status));
        this.baseMapper.updateById(skill);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void togglePublished(Long id, Boolean published) {
        AiSkill skill = getRequired(id);
        boolean targetPublished = Boolean.TRUE.equals(published);
        if (targetPublished) {
            String content;
            try {
                content = readSkillContent(skill);
            } catch (RuntimeException ex) {
                throw CheckedException.badRequest("读取 SKILL.md 失败: " + ex.getMessage());
            }
            if (StrUtil.isBlank(content)) {
                throw CheckedException.badRequest("SKILL.md 正文不能为空");
            }
            validateFrontMatter(content);
            skill.setContentDigest(AgentSkillContent.sha256(content));
            this.baseMapper.update(null, Wraps.<AiSkill>lbU()
                    .set(AiSkill::getPublished, false)
                    .eq(AiSkill::getCode, skill.getCode())
                    .ne(AiSkill::getId, skill.getId()));
        }
        skill.setPublished(targetPublished);
        this.baseMapper.updateById(skill);
    }

    private SkillPageResp toPageResp(AiSkill skill) {
        SkillPageResp resp = BeanUtil.toBean(skill, SkillPageResp.class);
        resp.setResourceCount(listResources(skill).size());
        return resp;
    }

    private SkillDetailResp toDetailResp(AiSkill skill, boolean includePreview) {
        SkillDetailResp resp = BeanUtil.toBean(skill, SkillDetailResp.class);
        List<String> resources = listResources(skill);
        resp.setResources(resources);
        resp.setResourceCount(resources.size());
        if (includePreview) {
            resp.setSkillContent(readSkillContent(skill));
            resp.setResourceContents(readResourceContents(skill, resources));
        }
        return resp;
    }

    private void fillSkillPackageFields(AiSkill skill, String code, String version, List<String> requiresTools,
                                        List<String> requiresToolsets, String contentDigest, String folder) {
        skill.setCode(code);
        skill.setVersion(version);
        skill.setTags(normalizeStrings(skill.getTags()));
        skill.setRequiresTools(requiresTools);
        skill.setRequiresToolsets(requiresToolsets);
        skill.setContentDigest(contentDigest);
        skill.setSkillPath(folder);
        skill.setSkillFile(folder + SKILL_FILE_NAME);
        skill.setResourceCount(listResources(skill).size());
    }

    private String normalizeSkillCode(String rawCode) {
        String code = trimToNull(rawCode);
        if (StrUtil.isBlank(code)) {
            throw CheckedException.badRequest("技能编码不能为空");
        }
        if (!SKILL_CODE_PATTERN.matcher(code).matches() || code.contains("..")) {
            throw CheckedException.badRequest("技能编码只能包含字母、数字、下划线或中划线，且不能包含路径片段");
        }
        return code;
    }

    private String normalizeSkillVersion(String rawVersion) {
        String version = trimToNull(rawVersion);
        if (StrUtil.isBlank(version)) {
            throw CheckedException.badRequest("技能版本不能为空");
        }
        if (!SKILL_VERSION_PATTERN.matcher(version).matches()) {
            throw CheckedException.badRequest("技能版本只能包含字母、数字、点、下划线或中划线");
        }
        return version;
    }

    private List<String> normalizeRequiredTools(Collection<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawValue : values) {
            String value = trimToNull(rawValue);
            if (value == null) {
                continue;
            }
            try {
                if (value.indexOf('@') != value.lastIndexOf('@')) {
                    throw new IllegalArgumentException("工具标识只能包含一个版本分隔符");
                }
                normalized.add(AgentToolIdentity.parse(value).identifier());
            } catch (IllegalArgumentException ex) {
                throw CheckedException.badRequest("依赖工具标识必须使用精确 name@version: " + value);
            }
        }
        return List.copyOf(normalized);
    }

    private List<String> normalizeRequiredToolsets(Collection<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String rawValue : values) {
            String value = trimToNull(rawValue);
            if (value == null) {
                continue;
            }
            if (!TOOLSET_ID_PATTERN.matcher(value).matches()) {
                throw CheckedException.badRequest("依赖 Toolset 标识格式不正确: " + value);
            }
            normalized.add(value);
        }
        return List.copyOf(normalized);
    }

    private SkillUploadPackage prepareUploadedSkillDirectory(List<MultipartFile> files, List<String> relativePaths) {
        if (CollUtil.isEmpty(files)) {
            throw CheckedException.badRequest("请上传技能目录");
        }
        if (CollUtil.isEmpty(relativePaths) || relativePaths.size() != files.size()) {
            throw CheckedException.badRequest("上传文件相对路径数量不匹配");
        }
        List<String> normalizedPaths = normalizeUploadPaths(relativePaths);
        Map<String, SkillUploadFile> uploadFiles = IntStream.range(0, files.size())
                .filter(index -> files.get(index) != null && !files.get(index).isEmpty())
                .mapToObj(index -> new SkillUploadFile(normalizedPaths.get(index), files.get(index)))
                .collect(Collectors.toMap(SkillUploadFile::relativePath, Function.identity(), (left, right) -> {
                    throw CheckedException.badRequest("技能目录不能包含重复文件: " + left.relativePath());
                }));
        String contentDigest = validateSkillDirectory(uploadFiles);
        return new SkillUploadPackage(uploadFiles.values()
                .stream()
                .sorted(Comparator.comparing(SkillUploadFile::relativePath))
                .toList(), contentDigest);
    }

    private List<String> normalizeUploadPaths(List<String> relativePaths) {
        List<String> paths = relativePaths.stream()
                .map(this::normalizeUploadPath)
                .toList();
        if (containsRootSkillFile(paths)) {
            return paths;
        }
        Optional<String> commonTopLevel = commonTopLevel(paths);
        if (commonTopLevel.isEmpty()) {
            throw CheckedException.badRequest("技能目录根目录必须包含 SKILL.md");
        }
        List<String> strippedPaths = paths.stream()
                .map(path -> path.substring(commonTopLevel.get().length() + 1))
                .toList();
        if (!containsRootSkillFile(strippedPaths)) {
            throw CheckedException.badRequest("技能目录根目录必须包含 SKILL.md");
        }
        return strippedPaths;
    }

    private String normalizeUploadPath(String rawPath) {
        String pathText = trimToNull(rawPath);
        if (StrUtil.isBlank(pathText)) {
            throw CheckedException.badRequest("上传文件路径不能为空");
        }
        String normalizedText = pathText.replace('\\', '/');
        if (normalizedText.startsWith("/") || normalizedText.contains("//")) {
            throw CheckedException.badRequest("文件路径不能包含绝对路径或空路径片段");
        }
        List<String> segments = StrUtil.split(normalizedText, '/');
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> StrUtil.equals(segment, "..") || StrUtil.equals(segment, "."))) {
            throw CheckedException.badRequest("文件路径不能包含路径穿越片段");
        }
        return String.join("/", segments);
    }

    private String normalizeSkillFilePath(String rawPath) {
        String pathText = trimToNull(rawPath);
        if (StrUtil.isBlank(pathText)) {
            throw CheckedException.badRequest("文件路径不能为空");
        }
        String normalizedText = pathText.replace('\\', '/');
        if (normalizedText.startsWith("/") || normalizedText.endsWith("/") || normalizedText.contains("//")) {
            throw CheckedException.badRequest("文件路径不能包含绝对路径、目录路径或空路径片段");
        }
        List<String> segments = StrUtil.split(normalizedText, '/');
        if (segments.isEmpty() || segments.stream().anyMatch(segment -> StrUtil.equals(segment, "..") || StrUtil.equals(segment, "."))) {
            throw CheckedException.badRequest("文件路径不能包含路径穿越片段");
        }
        return String.join("/", segments);
    }

    private boolean containsRootSkillFile(List<String> paths) {
        return paths.stream().anyMatch(SKILL_FILE_NAME::equals);
    }

    private Optional<String> commonTopLevel(List<String> paths) {
        if (paths.isEmpty() || paths.stream().anyMatch(path -> !path.contains("/"))) {
            return Optional.empty();
        }
        String first = StrUtil.subBefore(paths.getFirst(), "/", false);
        boolean sameTopLevel = paths.stream().allMatch(path -> first.equals(StrUtil.subBefore(path, "/", false)));
        return sameTopLevel ? Optional.of(first) : Optional.empty();
    }

    private String validateSkillDirectory(Map<String, SkillUploadFile> uploadFiles) {
        SkillUploadFile skillFile = uploadFiles.get(SKILL_FILE_NAME);
        if (skillFile == null) {
            throw CheckedException.badRequest("技能目录根目录必须包含 SKILL.md");
        }
        try {
            String content = new String(skillFile.file().getBytes(), StandardCharsets.UTF_8);
            validateFrontMatter(content);
            return AgentSkillContent.sha256(content);
        } catch (IOException ex) {
            throw CheckedException.badRequest("读取 SKILL.md 失败: " + ex.getMessage());
        }
    }

    private void validateFrontMatter(String content) {
        Matcher matcher = FRONT_MATTER_PATTERN.matcher(content);
        if (!matcher.find()) {
            throw CheckedException.badRequest("SKILL.md 必须以 YAML front matter 开头");
        }
        Map<String, Object> frontMatter;
        try {
            Object parsed = new Yaml(new SafeConstructor(new LoaderOptions())).load(matcher.group("yaml"));
            if (!(parsed instanceof Map<?, ?> values)) {
                throw CheckedException.badRequest("SKILL.md front matter 必须是 YAML 对象");
            }
            frontMatter = values.entrySet().stream().collect(Collectors.toMap(
                    entry -> String.valueOf(entry.getKey()), Map.Entry::getValue));
        } catch (CheckedException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw CheckedException.badRequest("SKILL.md front matter YAML 格式错误: " + failure.getMessage());
        }
        String name = String.valueOf(frontMatter.getOrDefault("name", ""));
        String description = String.valueOf(frontMatter.getOrDefault("description", ""));
        if (StrUtil.isBlank(name) || StrUtil.isBlank(description)) {
            throw CheckedException.badRequest("SKILL.md front matter 必须包含 name 和 description");
        }
    }

    private void ensureUniqueSkill(String code, String version, Long excludeId) {
        Long count = this.baseMapper.selectCount(Wraps.<AiSkill>lbQ()
                .eq(AiSkill::getCode, code)
                .eq(AiSkill::getVersion, version)
                .ne(excludeId != null, AiSkill::getId, excludeId));
        if (count != null && count > 0) {
            throw CheckedException.badRequest("技能编码和版本已存在");
        }
    }

    private void ensureSkillDirectoryAvailable(String folder) {
        if (!skillStorageService.list(folder).isEmpty()) {
            throw CheckedException.badRequest("技能目录已存在");
        }
    }

    private void uploadSkillDirectory(SkillUploadPackage uploadPackage, String folder) {
        for (SkillUploadFile uploadFile : uploadPackage.files()) {
            skillStorageService.upload(folder + uploadFile.relativePath(), uploadFile.file());
        }
    }

    private String readSkillContent(AiSkill skill) {
        return skillStorageService.readText(StrUtil.blankToDefault(skill.getSkillFile(), skillFile(skill.getCode())));
    }

    private Map<String, String> readResourceContents(AiSkill skill, List<String> resources) {
        String folder = StrUtil.blankToDefault(skill.getSkillPath(), skillFolder(skill.getCode()));
        Map<String, String> contents = new LinkedHashMap<>();
        for (String resource : resources) {
            contents.put(resource, skillStorageService.readText(folder + resource));
        }
        return contents;
    }

    private String resolveSkillObjectPath(AiSkill skill, String relativePath) {
        if (SKILL_FILE_NAME.equals(relativePath)) {
            return StrUtil.blankToDefault(skill.getSkillFile(), skillFile(skill.getCode()));
        }
        return StrUtil.blankToDefault(skill.getSkillPath(), skillFolder(skill.getCode())) + relativePath;
    }

    private void deleteSkillDirectory(String folder) {
        skillStorageService.deleteFolder(folder);
    }

    private void deleteSkillFolder(AiSkill skill) {
        skillStorageService.deleteFolder(storedSkillFolder(skill));
    }

    private void deleteSkillFolderSafely(AiSkill skill, String protectedFolder) {
        String storedFolder = storedSkillFolder(skill);
        if (protectedFolder != null && !protectedFolder.equals(storedFolder)
                && protectedFolder.startsWith(storedFolder)) {
            log.warn("跳过技能父目录递归删除，目标 revision 或版本目录位于其下，旧对象保留待清理: id={}, oldPath={}, targetPath={}",
                    skill.getId(), storedFolder, protectedFolder);
            return;
        }
        if (!storedFolder.equals(skillFolder(skill.getCode()))) {
            deleteSkillFolder(skill);
            return;
        }
        if (hasOtherSkillVersion(skill)) {
            log.warn("跳过 legacy 技能父目录递归删除，同编码仍存在其他版本，旧对象保留待清理: id={}, code={}, oldPath={}",
                    skill.getId(), skill.getCode(), storedFolder);
            return;
        }
        deleteSkillFolder(skill);
    }

    private void cleanupSkillFolderAfterCommit(AiSkill skill, String protectedFolder) {
        Runnable cleanup = () -> {
            try {
                deleteSkillFolderSafely(skill, protectedFolder);
            } catch (RuntimeException cleanupException) {
                log.warn("技能数据库事务已完成，但旧对象目录清理失败，将保留待清理: id={}, path={}",
                        skill.getId(), storedSkillFolder(skill), cleanupException);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {

                @Override
                public void afterCommit() {
                    cleanup.run();
                }
            });
            return;
        }
        cleanup.run();
    }

    private boolean hasOtherSkillVersion(AiSkill skill) {
        Long count = this.baseMapper.selectCount(Wraps.<AiSkill>lbQ()
                .eq(AiSkill::getCode, skill.getCode())
                .ne(AiSkill::getVersion, skill.getVersion())
                .ne(AiSkill::getId, skill.getId())
                .eq(AiSkill::getDeleted, false));
        return count != null && count > 0;
    }

    private List<String> listResources(AiSkill skill) {
        return listResources(storedSkillFolder(skill));
    }

    private List<String> listResources(String folder) {
        return skillStorageService.list(folder)
                .stream()
                .filter(path -> path.startsWith(folder))
                .map(path -> StrUtil.removePrefix(path, folder))
                .filter(path -> StrUtil.isNotBlank(path))
                .filter(path -> !SKILL_FILE_NAME.equals(path))
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private AiSkill getRequired(Long id) {
        return Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("AI技能不存在"));
    }

    private String skillFile(String code) {
        return skillFolder(code) + SKILL_FILE_NAME;
    }

    private String skillFolder(String code) {
        return normalizeRootPath(skillProperties.getRootPath()) + code + "/";
    }

    private String skillFolder(String code, String version) {
        return skillFolder(code) + version + "/";
    }

    /**
     * 为每次上传分配唯一 revision，使数据库竞争失败方只能清理自己的对象目录。
     * @param code 编码
     * @param version version 参数
     * @return 处理结果
     */
    private String skillRevisionFolder(String code, String version) {
        return skillFolder(code, version) + ".revisions/" + UUID.randomUUID() + "/";
    }

    private String storedSkillFolder(AiSkill skill) {
        return StrUtil.blankToDefault(skill.getSkillPath(), skillFolder(skill.getCode()));
    }

    private static List<String> normalizeStrings(Collection<String> values) {
        if (CollUtil.isEmpty(values)) {
            return List.of();
        }
        return values.stream()
                .map(SkillServiceImpl::trimToNull)
                .filter(StrUtil::isNotBlank)
                .distinct()
                .toList();
    }

    private static String trimToNull(String value) {
        return StrUtil.isBlank(value) ? null : value.trim();
    }

    private static String normalizeRootPath(String rootPath) {
        String root = StrUtil.blankToDefault(rootPath, "ai/skills").trim().replace('\\', '/');
        root = StrUtil.removePrefix(root, "/");
        return StrUtil.addSuffixIfNot(root, "/");
    }

    private record SkillUploadPackage(List<SkillUploadFile> files, String contentDigest) {
    }

    private record SkillUploadFile(String relativePath, MultipartFile file) {
    }
}
