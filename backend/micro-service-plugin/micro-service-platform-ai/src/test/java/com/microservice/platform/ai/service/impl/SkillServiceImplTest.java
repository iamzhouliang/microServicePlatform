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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.wrap.query.LbqWrapper;
import com.microservice.framework.ai.harness.skill.AgentSkillContent;
import com.microservice.platform.ai.core.config.SkillProperties;
import com.microservice.platform.ai.domain.dto.req.SkillPageReq;
import com.microservice.platform.ai.domain.dto.req.SkillSaveReq;
import com.microservice.platform.ai.domain.dto.resp.SkillDetailResp;
import com.microservice.platform.ai.domain.dto.resp.SkillPageResp;
import com.microservice.platform.ai.domain.entity.AiSkill;
import com.microservice.platform.ai.repository.SkillMapper;
import com.microservice.platform.ai.service.SkillStorageService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.TimeUnit.SECONDS;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AI Skills 管理服务")
class SkillServiceImplTest {

    @BeforeAll
    static void initializeMybatisTableMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AiSkill.class);
    }

    @Test
    @DisplayName("上传技能文件夹时写入 OSS 前缀并保存目录路径引用")
    void createUploadsSkillFolderToOssAndStoresPathReference() throws Exception {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(0L);

        service.create(saveReq(), skillFiles(), skillRelativePaths());

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).insert(captor.capture());
        AiSkill saved = captor.getValue();
        assertThat(storage.objects).containsOnlyKeys(
                saved.getSkillFile(), saved.getSkillPath() + "references/query-guide.md",
                saved.getSkillPath() + "scripts/build.py");
        assertThat(storage.objects.get(saved.getSkillFile())).isEqualTo(skillMarkdown());

        assertThat(saved.getCode()).isEqualTo("platform-search");
        assertThat(saved.getSkillPath())
                .matches("ai/skills/platform-search/1\\.0\\.0/\\.revisions/[0-9a-f-]{36}/");
        assertThat(saved.getSkillFile()).isEqualTo(saved.getSkillPath() + "SKILL.md");
        assertThat(saved.getResourceCount()).isEqualTo(2);
        assertThat(saved.getTags()).containsExactly("system", "search");
        assertThat(saved.getVersion()).isEqualTo("1.0.0");
        assertThat(saved.getRequiresTools()).containsExactly("iam.search-user@1.0.0");
        assertThat(saved.getContentDigest()).isEqualTo(AgentSkillContent.sha256(skillMarkdown()));
        assertThat(saved.getStatus()).isTrue();
        assertThat(saved.getPublished()).isFalse();
    }

    @Test
    @DisplayName("上传技能文件夹时可剥离浏览器传入的顶层文件夹名")
    void createStripsSelectedTopLevelFolder() throws Exception {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(0L);

        service.create(saveReq(), skillFiles(), List.of(
                "selected-folder/SKILL.md",
                "selected-folder/references/query-guide.md",
                "selected-folder/scripts/build.ps1"));

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).insert(captor.capture());
        String folder = captor.getValue().getSkillPath();
        assertThat(storage.objects).containsKeys(folder + "SKILL.md", folder + "references/query-guide.md");
        assertThat(storage.objects).doesNotContainKey(folder + "selected-folder/SKILL.md");
    }

    @Test
    @DisplayName("相同编码的不同版本可创建并写入独立目录")
    void createAllowsSameCodeWithDifferentVersion() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("ai/skills/platform-search/1.0.0/SKILL.md", skillMarkdown());
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setVersion("2.0.0");

        service.create(req, List.of(multipart("SKILL.md", skillMarkdown())), List.of("SKILL.md"));

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).insert(captor.capture());
        AiSkill inserted = captor.getValue();
        assertThat(storage.objects).containsKeys(
                "ai/skills/platform-search/1.0.0/SKILL.md", inserted.getSkillFile());
        assertThat(inserted.getVersion()).isEqualTo("2.0.0");
        assertThat(inserted.getRequiresToolsets()).containsExactly("module:iam:user-management");
    }

    @Test
    @DisplayName("相同编码和版本重复创建时拒绝且不写对象存储")
    void createRejectsDuplicateCodeAndVersion() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> service.create(saveReq(), skillFiles(), skillRelativePaths()))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("编码和版本已存在");

        assertThat(storage.objects).isEmpty();
        verify(skillMapper, never()).insert(any(AiSkill.class));
    }

    @Test
    @DisplayName("并发创建同编码版本时 loser 只清理自己的目录并保留 winner 完整包")
    void concurrentCreateLoserCannotDeleteWinnerPackage() throws Exception {
        ConcurrentSkillStorage storage = new ConcurrentSkillStorage();
        SkillMapper skillMapper = concurrentInsertMapper(storage);
        SkillServiceImpl service = service(skillMapper, storage);
        String firstMarkdown = skillMarkdown() + "\n第一份正文。";
        String secondMarkdown = skillMarkdown() + "\n第二份正文。";

        runConcurrently(
                () -> service.create(saveReq(), packageFiles(firstMarkdown, "第一份附件"), skillRelativePaths()),
                () -> service.create(saveReq(), packageFiles(secondMarkdown, "第二份附件"), skillRelativePaths()));

        AiSkill winner = storage.winner.get();
        assertThat(winner).isNotNull();
        String winnerMarkdown = storage.objects.get(winner.getSkillFile());
        assertThat(winnerMarkdown).as("winner 的 SKILL.md 不得被 loser 清理").isNotNull();
        assertThat(AgentSkillContent.sha256(winnerMarkdown)).isEqualTo(winner.getContentDigest());
        String expectedAttachment = winnerMarkdown.equals(firstMarkdown) ? "第一份附件" : "第二份附件";
        assertThat(storage.objects.get(winner.getSkillPath() + "references/query-guide.md"))
                .isEqualTo(expectedAttachment);
        assertThat(storage.objects).containsKey(winner.getSkillPath() + "scripts/build.py");
        assertThat(storage.deletedFolders).noneMatch(winner.getSkillPath()::equals);
    }

    @Test
    @DisplayName("并发修改到同编码版本时 loser 只清理自己的目录并保留 winner 完整包")
    void concurrentModifyLoserCannotDeleteWinnerPackage() throws Exception {
        ConcurrentSkillStorage storage = new ConcurrentSkillStorage();
        final SkillMapper skillMapper = concurrentInsertMapper(storage);
        AiSkill firstSource = versionedSkillEntity(true);
        firstSource.setId(10L);
        firstSource.setCode("source-first");
        firstSource.setVersion("0.9.0");
        AiSkill secondSource = versionedSkillEntity(true);
        secondSource.setId(20L);
        secondSource.setCode("source-second");
        secondSource.setVersion("0.9.0");
        when(skillMapper.selectById(10L)).thenReturn(firstSource);
        when(skillMapper.selectById(20L)).thenReturn(secondSource);
        SkillServiceImpl service = service(skillMapper, storage);
        String firstMarkdown = skillMarkdown() + "\n第一份修改正文。";
        String secondMarkdown = skillMarkdown() + "\n第二份修改正文。";

        runConcurrently(
                () -> service.modify(10L, saveReq(), packageFiles(firstMarkdown, "第一份修改附件"),
                        skillRelativePaths()),
                () -> service.modify(20L, saveReq(), packageFiles(secondMarkdown, "第二份修改附件"),
                        skillRelativePaths()));

        AiSkill winner = storage.winner.get();
        assertThat(winner).isNotNull();
        String winnerMarkdown = storage.objects.get(winner.getSkillFile());
        assertThat(winnerMarkdown).as("winner 的 SKILL.md 不得被 loser 清理").isNotNull();
        assertThat(AgentSkillContent.sha256(winnerMarkdown)).isEqualTo(winner.getContentDigest());
        String expectedAttachment = winnerMarkdown.equals(firstMarkdown) ? "第一份修改附件" : "第二份修改附件";
        assertThat(storage.objects.get(winner.getSkillPath() + "references/query-guide.md"))
                .isEqualTo(expectedAttachment);
        assertThat(storage.objects).containsKey(winner.getSkillPath() + "scripts/build.py");
        assertThat(storage.deletedFolders).noneMatch(winner.getSkillPath()::equals);
    }

    @Test
    @DisplayName("上传技能文件夹时拒绝缺少根目录 SKILL.md 的目录")
    void createRejectsDirectoryWithoutSkillMarkdown() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, new InMemorySkillStorage());
        when(skillMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(saveReq(),
                List.of(multipart("references/query-guide.md", "只返回启用菜单。")),
                List.of("references/query-guide.md")))
                        .isInstanceOf(CheckedException.class)
                        .hasMessageContaining("SKILL.md");
    }

    @Test
    @DisplayName("上传技能文件夹时拒绝文件相对路径穿越")
    void createRejectsUnsafeUploadRelativePath() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, new InMemorySkillStorage());
        when(skillMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(saveReq(),
                List.of(multipart("SKILL.md", skillMarkdown())),
                List.of("../SKILL.md")))
                        .isInstanceOf(CheckedException.class)
                        .hasMessageContaining("文件路径不能包含");
    }

    @Test
    @DisplayName("上传技能文件夹时拒绝 SKILL.md 缺少 front matter")
    void createRejectsInvalidSkillMarkdownFrontMatter() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, new InMemorySkillStorage());
        when(skillMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.create(saveReq(),
                List.of(multipart("SKILL.md", "Write the skill instructions here.")),
                List.of("SKILL.md")))
                        .isInstanceOf(CheckedException.class)
                        .hasMessageContaining("front matter");
    }

    @Test
    @DisplayName("详情和预览从 OSS 返回 SKILL.md 内容、层级资源清单和资源内容")
    void detailAndPreviewReturnSkillMarkdownAndResourcesFromOss() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        SkillDetailResp detail = service.detail(10L);
        SkillDetailResp preview = service.preview(10L);

        assertThat(detail.getSkillContent()).contains("references/query-guide.md");
        assertThat(detail.getResources()).containsExactly(
                "references/query-guide.md",
                "scripts/build.ps1",
                "scripts/build.py");
        assertThat(detail.getResourceContents())
                .containsEntry("references/query-guide.md", "只返回启用菜单。")
                .containsEntry("scripts/build.py", "print('ignored')")
                .containsEntry("scripts/build.ps1", "Write-Host ignored");
        assertThat(preview.getSkillContent()).isEqualTo(detail.getSkillContent());
        assertThat(preview.getResources()).containsExactly(
                "references/query-guide.md",
                "scripts/build.ps1",
                "scripts/build.py");
        assertThat(preview.getResourceContents()).isEqualTo(detail.getResourceContents());
        assertThat(preview.getResources()).doesNotContain("SKILL.md");
    }

    @Test
    @DisplayName("详情预览和删除使用实体保存的技能目录路径")
    void detailPreviewAndRemoveUseStoredSkillPathReference() {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("oss/skills/platform-search/SKILL.md", skillMarkdown());
        storage.objects.put("oss/skills/platform-search/references/query-guide.md", "只返回启用菜单。");
        storage.objects.put("oss/skills/platform-search/scripts/build.py", "print('ignored')");
        storage.objects.put("oss/skills/platform-search/scripts/build.ps1", "Write-Host ignored");
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage, "unused/skills");
        AiSkill skill = skillEntity();
        skill.setSkillPath("oss/skills/platform-search/");
        skill.setSkillFile("oss/skills/platform-search/SKILL.md");
        when(skillMapper.selectById(10L)).thenReturn(skill);

        SkillDetailResp detail = service.detail(10L);
        service.remove(10L);

        assertThat(detail.getSkillContent()).isEqualTo(skillMarkdown());
        assertThat(detail.getResources()).containsExactly(
                "references/query-guide.md",
                "scripts/build.ps1",
                "scripts/build.py");
        assertThat(detail.getResourceContents()).containsEntry("scripts/build.py", "print('ignored')");
        assertThat(storage.objects).doesNotContainKeys(
                "oss/skills/platform-search/SKILL.md",
                "oss/skills/platform-search/references/query-guide.md");
    }

    @Test
    @DisplayName("下载技能时使用实体保存的技能目录路径并返回 zip 包")
    void downloadUsesStoredSkillPathReferenceAndReturnsZipPackage() throws Exception {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("oss/skills/platform-search/SKILL.md", skillMarkdown());
        storage.objects.put("oss/skills/platform-search/references/query-guide.md", "只返回启用菜单。");
        storage.objects.put("oss/skills/platform-search/scripts/build.py", "print('ignored')");
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage, "unused/skills");
        AiSkill skill = skillEntity();
        skill.setSkillPath("oss/skills/platform-search/");
        skill.setSkillFile("oss/skills/platform-search/SKILL.md");
        when(skillMapper.selectById(10L)).thenReturn(skill);

        byte[] zipBytes = service.download(10L);

        assertThat(zipBytes).isEqualTo("skill zip bytes".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.downloadedFolders).containsExactly("oss/skills/platform-search/");
    }

    @Test
    @DisplayName("在线编辑资源文件时按实体保存的技能目录路径写回 OSS")
    void updateFileWritesResourceContentUnderStoredSkillPath() {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("oss/skills/platform-search/SKILL.md", skillMarkdown());
        storage.objects.put("oss/skills/platform-search/references/query-guide.md", "只返回启用菜单。");
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage, "unused/skills");
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        skill.setSkillPath("oss/skills/platform-search/");
        skill.setSkillFile("oss/skills/platform-search/SKILL.md");
        when(skillMapper.selectById(10L)).thenReturn(skill);

        service.updateFile(10L, "references/query-guide.md", "只返回启用菜单和按钮权限。");

        assertThat(storage.objects.get("oss/skills/platform-search/references/query-guide.md"))
                .isEqualTo("只返回启用菜单和按钮权限。");
        assertThat(storage.writtenObjects).containsExactly("oss/skills/platform-search/references/query-guide.md");
    }

    @Test
    @DisplayName("在线编辑资源文件时允许保存空文本")
    void updateFileAllowsEmptyTextContent() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(skill);

        service.updateFile(10L, "references/query-guide.md", "");

        assertThat(storage.objects.get("ai/skills/platform-search/references/query-guide.md")).isEmpty();
        assertThat(storage.writtenObjects).containsExactly("ai/skills/platform-search/references/query-guide.md");
    }

    @Test
    @DisplayName("在线编辑 SKILL.md 时写回实体保存的入口文件并校验 front matter")
    void updateFileWritesSkillMarkdownAndValidatesFrontMatter() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(skill);
        String updatedSkillMarkdown = """
                ---
                name: platform-search
                description: 更新后的技能说明
                ---

                使用 references/query-guide.md 中的查询规范。
                """;

        service.updateFile(10L, "SKILL.md", updatedSkillMarkdown);

        assertThat(storage.objects.get("ai/skills/platform-search/SKILL.md")).isEqualTo(updatedSkillMarkdown);
        assertThat(storage.writtenObjects).containsExactly("ai/skills/platform-search/SKILL.md");
        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(captor.getValue().getContentDigest())
                .isEqualTo(AgentSkillContent.sha256(updatedSkillMarkdown));
    }

    @Test
    @DisplayName("已发布技能在线编辑 SKILL.md 时要求创建新版本且不写对象存储")
    void updateFileRejectsPublishedSkillMarkdownInPlace() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        assertThatThrownBy(() -> service.updateFile(10L, "SKILL.md", skillMarkdown() + "\n变更"))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("新版本");

        assertThat(storage.writtenObjects).isEmpty();
        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("已发布技能在线编辑资源文件时要求创建新版本")
    void updateFileRejectsPublishedResourceInPlace() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        assertThatThrownBy(() -> service.updateFile(10L, "references/query-guide.md", "变更资源"))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("创建新版本");

        assertThat(storage.writtenObjects).isEmpty();
        assertThat(storage.objects.get("ai/skills/platform-search/references/query-guide.md"))
                .isEqualTo("只返回启用菜单。");
        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("在线编辑拒绝路径穿越和不存在的目录内文件")
    void updateFileRejectsUnsafeOrUnknownPath() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(skill);

        assertThatThrownBy(() -> service.updateFile(10L, "../SKILL.md", skillMarkdown()))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("路径");
        assertThatThrownBy(() -> service.updateFile(10L, "references/missing.md", "new"))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("文件不存在");
        assertThatThrownBy(() -> service.updateFile(10L, "SKILL.md", "missing front matter"))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("front matter");
    }

    @Test
    @DisplayName("分页响应返回技能目录摘要而不是工具或 MCP 绑定摘要")
    void pageListReturnsSkillDirectorySummary() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, existingStorage());
        Page<AiSkill> mapperPage = new Page<>(1, 10);
        mapperPage.setRecords(List.of(skillEntity()));
        mapperPage.setTotal(1);
        when(skillMapper.selectPage(any(), any())).thenReturn(mapperPage);

        SkillPageReq req = new SkillPageReq();
        req.setName("平台");
        req.setStatus(true);
        IPage<SkillPageResp> result = service.pageList(req);

        assertThat(result.getRecords()).hasSize(1);
        SkillPageResp resp = result.getRecords().getFirst();
        assertThat(resp.getCode()).isEqualTo("platform-search");
        assertThat(resp.getSkillPath()).isEqualTo("ai/skills/platform-search/");
        assertThat(resp.getSkillFile()).isEqualTo("ai/skills/platform-search/SKILL.md");
        assertThat(resp.getResourceCount()).isEqualTo(3);
        assertThat(resp.getPublished()).isTrue();
    }

    @Test
    @DisplayName("已发布技能修改编码时插入新快照并保留旧目录")
    void modifyPublishedSkillCodeChangeInsertsSnapshotAndKeepsOldDirectory() throws Exception {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setCode("platform-search-new");

        service.modify(10L, req, skillFiles(), skillRelativePaths());

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).insert(captor.capture());
        AiSkill inserted = captor.getValue();
        assertThat(storage.objects).containsKey("ai/skills/platform-search/SKILL.md");
        assertThat(storage.objects).containsKeys(
                inserted.getSkillFile(), inserted.getSkillPath() + "references/query-guide.md",
                inserted.getSkillPath() + "scripts/build.py");

        verify(skillMapper, never()).updateById(any(AiSkill.class));
        assertThat(inserted.getId()).isNull();
        assertThat(inserted.getCode()).isEqualTo("platform-search-new");
        assertThat(inserted.getSkillPath())
                .matches("ai/skills/platform-search-new/1\\.0\\.0/\\.revisions/[0-9a-f-]{36}/");
        assertThat(inserted.getSkillFile()).isEqualTo(inserted.getSkillPath() + "SKILL.md");
        assertThat(inserted.getStatus()).isTrue();
        assertThat(inserted.getPublished()).isTrue();
    }

    @Test
    @DisplayName("已发布技能同版本正文摘要变化时拒绝且保留原目录")
    void modifyRejectsPublishedContentChangeWithoutVersionBump() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(0L);
        String changedMarkdown = skillMarkdown() + "\n仅修改正文。";

        assertThatThrownBy(() -> service.modify(10L, saveReq(),
                List.of(multipart("SKILL.md", changedMarkdown)), List.of("SKILL.md")))
                        .isInstanceOf(CheckedException.class)
                        .hasMessageContaining("新版本");

        assertThat(storage.objects.get("ai/skills/platform-search/SKILL.md")).isEqualTo(skillMarkdown());
        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("已发布技能即使正文相同也拒绝原身份修改")
    void modifyRejectsPublishedSkillWithUnchangedIdentityAndContent() {
        InMemorySkillStorage storage = existingStorage();
        Map<String, String> originalObjects = new LinkedHashMap<>(storage.objects);
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.modify(10L, saveReq(), skillFiles(), skillRelativePaths()))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("创建新版本");

        assertThat(storage.objects).isEqualTo(originalObjects);
        assertThat(storage.deletedFolders).isEmpty();
        verify(skillMapper, never()).updateById(any(AiSkill.class));
        verify(skillMapper, never()).insert(any(AiSkill.class));
    }

    @Test
    @DisplayName("未发布同身份修改上传中途失败时保留 live 目录并清理 staging")
    void modifyUnpublishedSameIdentityUploadFailureKeepsLiveDirectoryAndCleansStaging() {
        InMemorySkillStorage storage = versionedStorage();
        final Map<String, String> originalObjects = new LinkedHashMap<>(storage.objects);
        storage.failUploadAt = 2;
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));
        when(skillMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> service.modify(10L, saveReq(), skillFiles(), skillRelativePaths()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("模拟上传失败");

        assertThat(storage.objects).isEqualTo(originalObjects);
        assertThat(storage.objects.keySet()).noneMatch(path -> path.contains("/.revisions/"));
        assertThat(storage.deletedFolders).anyMatch(path -> path.contains("/.revisions/"));
        verify(skillMapper, never()).updateById(any(AiSkill.class));
        verify(skillMapper, never()).insert(any(AiSkill.class));
    }

    @Test
    @DisplayName("未发布同身份修改先完整写 staging 再切换数据库指针")
    void modifyUnpublishedSameIdentityStagesAllObjectsBeforeUpdatingPointer() {
        InMemorySkillStorage storage = versionedStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));
        when(skillMapper.selectCount(any())).thenReturn(0L);
        AtomicReference<String> revisionFolder = new AtomicReference<>();
        when(skillMapper.updateById(any(AiSkill.class))).thenAnswer(invocation -> {
            AiSkill updated = invocation.getArgument(0);
            revisionFolder.set(updated.getSkillPath());
            assertThat(updated.getSkillPath())
                    .matches("ai/skills/platform-search/1\\.0\\.0/\\.revisions/[0-9a-f-]{36}/");
            assertThat(storage.objects).containsKeys(
                    updated.getSkillFile(),
                    updated.getSkillPath() + "references/query-guide.md",
                    updated.getSkillPath() + "scripts/build.py");
            assertThat(storage.objects).containsKey("ai/skills/platform-search/1.0.0/SKILL.md");
            return 1;
        });

        service.modify(10L, saveReq(), skillFiles(), skillRelativePaths());

        assertThat(revisionFolder.get()).isNotBlank();
        assertThat(storage.objects).containsKey(revisionFolder.get() + "SKILL.md");
        assertThat(storage.objects).containsKey("ai/skills/platform-search/1.0.0/SKILL.md");
        assertThat(storage.deletedFolders).doesNotContain("ai/skills/platform-search/1.0.0/");
    }

    @Test
    @DisplayName("事务内修改身份时提交后才删除旧目录")
    void modifyDefersOldDirectoryCleanupUntilAfterCommit() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill legacy = skillEntity();
        legacy.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(legacy);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setCode("platform-search-new");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.modify(10L, req, skillFiles(), skillRelativePaths());

            assertThat(storage.objects).containsKey("ai/skills/platform-search/SKILL.md");
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).isNotEmpty();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            assertThat(storage.objects).doesNotContainKey("ai/skills/platform-search/SKILL.md");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务内修改身份回滚时保留旧目录")
    void modifyRollbackKeepsOldDirectory() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill legacy = skillEntity();
        legacy.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(legacy);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setCode("platform-search-new");

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.modify(10L, req, skillFiles(), skillRelativePaths());
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(storage.objects).containsKey("ai/skills/platform-search/SKILL.md");
            assertThat(storage.deletedFolders).doesNotContain("ai/skills/platform-search/");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("未发布同身份修改数据库更新失败时立即清理 staging 并保留 live")
    void modifyUnpublishedSameIdentityUpdateFailureCleansStagingImmediately() {
        InMemorySkillStorage storage = versionedStorage();
        Map<String, String> originalObjects = new LinkedHashMap<>(storage.objects);
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));
        when(skillMapper.selectCount(any())).thenReturn(0L);
        when(skillMapper.updateById(any(AiSkill.class))).thenThrow(new IllegalStateException("数据库更新失败"));

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThatThrownBy(() -> service.modify(10L, saveReq(), skillFiles(), skillRelativePaths()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("数据库更新失败");

            assertThat(storage.objects).isEqualTo(originalObjects);
            assertThat(storage.objects.keySet()).noneMatch(path -> path.contains("/.revisions/"));
            assertThat(storage.deletedFolders).anyMatch(path -> path.contains("/.revisions/"));
            assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("已发布技能提升版本后插入新行并保留旧行与旧目录")
    void modifyPublishedSkillVersionBumpInsertsNewVersionAndKeepsOldVersion() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setVersion("2.0.0");
        String changedMarkdown = skillMarkdown() + "\n第二版正文。";

        service.modify(10L, req,
                List.of(multipart("SKILL.md", changedMarkdown)), List.of("SKILL.md"));

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).insert(captor.capture());
        verify(skillMapper, never()).updateById(any(AiSkill.class));
        AiSkill inserted = captor.getValue();
        assertThat(inserted.getId()).isNull();
        assertThat(inserted.getVersion()).isEqualTo("2.0.0");
        assertThat(inserted.getSkillPath())
                .matches("ai/skills/platform-search/2\\.0\\.0/\\.revisions/[0-9a-f-]{36}/");
        assertThat(inserted.getSkillFile()).isEqualTo(inserted.getSkillPath() + "SKILL.md");
        assertThat(inserted.getContentDigest()).isEqualTo(AgentSkillContent.sha256(changedMarkdown));
        assertThat(inserted.getStatus()).isTrue();
        assertThat(inserted.getPublished()).isTrue();
        assertThat(storage.objects.get("ai/skills/platform-search/SKILL.md")).isEqualTo(skillMarkdown());
        assertThat(storage.objects.get(inserted.getSkillFile())).isEqualTo(changedMarkdown);
    }

    @Test
    @DisplayName("已发布技能升版插入失败时只清理新目录")
    void modifyPublishedSkillVersionBumpFailureKeepsOldVersionAndCleansNewDirectory() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(0L);
        when(skillMapper.insert(any(AiSkill.class))).thenThrow(new IllegalStateException("数据库写入失败"));
        SkillSaveReq req = saveReq();
        req.setVersion("2.0.0");

        assertThatThrownBy(() -> service.modify(10L, req,
                List.of(multipart("SKILL.md", skillMarkdown() + "\n第二版正文。")), List.of("SKILL.md")))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessage("数据库写入失败");

        assertThat(storage.objects.get("ai/skills/platform-search/SKILL.md")).isEqualTo(skillMarkdown());
        assertThat(storage.objects.keySet()).noneMatch(path -> path.startsWith("ai/skills/platform-search/2.0.0/"));
        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("未发布 legacy 技能升版时保留父目录和新版本子目录")
    void modifyUnpublishedLegacyVersionKeepsParentAndNewVersionDirectory() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        AiSkill legacy = skillEntity();
        legacy.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(legacy);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setVersion("2.0.0");
        String changedMarkdown = skillMarkdown() + "\n第二版正文。";

        service.modify(10L, req,
                List.of(multipart("SKILL.md", changedMarkdown)), List.of("SKILL.md"));

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(storage.objects.get("ai/skills/platform-search/SKILL.md")).isEqualTo(skillMarkdown());
        assertThat(storage.objects.get(captor.getValue().getSkillFile())).isEqualTo(changedMarkdown);
        assertThat(storage.deletedFolders).doesNotContain("ai/skills/platform-search/");
        verify(skillMapper).selectCount(any());
    }

    @Test
    @DisplayName("删除一个版本时不删除同编码其他版本目录")
    void removeDeletesOnlyStoredVersionDirectory() {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("ai/skills/platform-search/1.0.0/SKILL.md", skillMarkdown());
        storage.objects.put("ai/skills/platform-search/2.0.0/SKILL.md", skillMarkdown() + "\n第二版正文。");
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setSkillPath("ai/skills/platform-search/1.0.0/");
        skill.setSkillFile("ai/skills/platform-search/1.0.0/SKILL.md");
        when(skillMapper.selectById(10L)).thenReturn(skill);

        service.remove(10L);

        assertThat(storage.objects).doesNotContainKey("ai/skills/platform-search/1.0.0/SKILL.md");
        assertThat(storage.objects).containsKey("ai/skills/platform-search/2.0.0/SKILL.md");
    }

    @Test
    @DisplayName("删除 legacy 技能时若存在同编码其他版本则只删数据库记录")
    void removeLegacySkillWithOtherVersionKeepsParentDirectory() {
        InMemorySkillStorage storage = existingStorage();
        storage.objects.put("ai/skills/platform-search/2.0.0/SKILL.md", skillMarkdown() + "\n第二版正文。");
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());
        when(skillMapper.selectCount(any())).thenReturn(1L);

        service.remove(10L);

        verify(skillMapper).deleteById(10L);
        assertThat(storage.deletedFolders).isEmpty();
        assertThat(storage.objects).containsKeys(
                "ai/skills/platform-search/SKILL.md",
                "ai/skills/platform-search/2.0.0/SKILL.md");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<LbqWrapper<AiSkill>> wrapperCaptor = ArgumentCaptor.forClass(LbqWrapper.class);
        verify(skillMapper).selectCount(wrapperCaptor.capture());
        LbqWrapper<AiSkill> wrapper = wrapperCaptor.getValue();
        assertThat(wrapper.getCustomSqlSegment().toLowerCase())
                .contains("code", "version", "id", "deleted");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains("platform-search", "1.0.0", 10L, false);
    }

    @Test
    @DisplayName("事务内删除技能时提交后才删除版本目录")
    void removeDefersDirectoryCleanupUntilAfterCommit() {
        InMemorySkillStorage storage = versionedStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.remove(10L);

            assertThat(storage.objects).containsKey("ai/skills/platform-search/1.0.0/SKILL.md");
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();
            assertThat(synchronizations).isNotEmpty();
            synchronizations.forEach(TransactionSynchronization::afterCommit);
            assertThat(storage.objects).doesNotContainKey("ai/skills/platform-search/1.0.0/SKILL.md");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("事务内删除技能回滚时保留版本目录")
    void removeRollbackKeepsDirectory() {
        InMemorySkillStorage storage = versionedStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.remove(10L);
            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(
                            TransactionSynchronization.STATUS_ROLLED_BACK));

            assertThat(storage.objects).containsKey("ai/skills/platform-search/1.0.0/SKILL.md");
            assertThat(storage.deletedFolders).doesNotContain("ai/skills/platform-search/1.0.0/");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("提交后目录清理失败时只记录告警")
    void afterCommitCleanupFailureDoesNotEscape() {
        InMemorySkillStorage storage = versionedStorage();
        storage.failDelete = true;
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(versionedSkillEntity(false));

        TransactionSynchronizationManager.initSynchronization();
        try {
            service.remove(10L);
            List<TransactionSynchronization> synchronizations = TransactionSynchronizationManager.getSynchronizations();

            assertThatCode(() -> synchronizations.forEach(TransactionSynchronization::afterCommit))
                    .doesNotThrowAnyException();
            assertThat(storage.objects).containsKey("ai/skills/platform-search/1.0.0/SKILL.md");
            verify(skillMapper).deleteById(10L);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("未发布 legacy 技能修改编码且无其他版本时删除旧目录")
    void modifyUnpublishedLegacyCodeToIndependentDirectoryDeletesOldDirectory() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        AiSkill legacy = skillEntity();
        legacy.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(legacy);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setCode("platform-search-new");

        service.modify(10L, req, skillFiles(), skillRelativePaths());

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(storage.deletedFolders).contains("ai/skills/platform-search/");
        assertThat(storage.objects).doesNotContainKey("ai/skills/platform-search/SKILL.md");
        assertThat(storage.objects).containsKey(captor.getValue().getSkillFile());
    }

    @Test
    @DisplayName("创建技能时拒绝缺少版本的工具标识且不上传文件")
    void createRejectsToolNameWithoutExactVersion() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setRequiresTools(List.of("iam.search-user"));

        assertThatThrownBy(() -> service.create(req, skillFiles(), skillRelativePaths()))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("name@version");

        assertThat(storage.objects).isEmpty();
        verify(skillMapper, never()).insert(any(AiSkill.class));
    }

    @Test
    @DisplayName("创建技能时拒绝非法 Toolset 标识且不上传文件")
    void createRejectsInvalidToolsetId() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        InMemorySkillStorage storage = new InMemorySkillStorage();
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectCount(any())).thenReturn(0L);
        SkillSaveReq req = saveReq();
        req.setRequiresToolsets(List.of("../../越界目录"));

        assertThatThrownBy(() -> service.create(req, skillFiles(), skillRelativePaths()))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("Toolset 标识格式不正确");

        assertThat(storage.objects).isEmpty();
        verify(skillMapper, never()).insert(any(AiSkill.class));
    }

    @Test
    @DisplayName("删除技能时同步删除 OSS 前缀")
    void removeDeletesSkillFolderPrefixInOss() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        service.remove(10L);

        verify(skillMapper).deleteById(10L);
        assertThat(storage.objects).doesNotContainKeys(
                "ai/skills/platform-search/SKILL.md",
                "ai/skills/platform-search/references/query-guide.md");
    }

    @Test
    @DisplayName("状态切换只更新目标技能状态")
    void toggleStatusUpdatesOnlyStatus() {
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, existingStorage());
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        service.toggleStatus(10L, false);

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(10L);
        assertThat(captor.getValue().getStatus()).isFalse();
        assertThat(captor.getValue().getName()).isEqualTo("平台检索");
    }

    @Test
    @DisplayName("重新发布 legacy 技能时读取正文并写入真实摘要")
    void togglePublishedTrueReadsLegacyContentAndStoresDigest() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        final SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        skill.setContentDigest(null);
        skill.setSkillFile(null);
        when(skillMapper.selectById(10L)).thenReturn(skill);

        service.togglePublished(10L, true);

        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPublished()).isTrue();
        assertThat(captor.getValue().getContentDigest()).isEqualTo(AgentSkillContent.sha256(skillMarkdown()));
        assertThat(storage.readObjects).containsExactly("ai/skills/platform-search/SKILL.md");
    }

    @Test
    @DisplayName("重新发布技能正文非法时不更新数据库")
    void togglePublishedTrueRejectsInvalidSkillContentWithoutUpdating() {
        InMemorySkillStorage storage = existingStorage();
        storage.objects.put("ai/skills/platform-search/SKILL.md", "正文缺少 front matter");
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(skill);

        assertThatThrownBy(() -> service.togglePublished(10L, true))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("front matter");

        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("重新发布技能正文读取失败时不更新数据库")
    void togglePublishedTrueReadFailureDoesNotUpdate() {
        InMemorySkillStorage storage = existingStorage();
        storage.readFailure = new IllegalStateException("对象存储不可用");
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        AiSkill skill = skillEntity();
        skill.setPublished(false);
        when(skillMapper.selectById(10L)).thenReturn(skill);

        assertThatThrownBy(() -> service.togglePublished(10L, true))
                .isInstanceOf(CheckedException.class)
                .hasMessageContaining("读取 SKILL.md 失败");

        verify(skillMapper, never()).updateById(any(AiSkill.class));
    }

    @Test
    @DisplayName("取消发布不读取正文")
    void togglePublishedFalseDoesNotReadContent() {
        InMemorySkillStorage storage = existingStorage();
        SkillMapper skillMapper = mock(SkillMapper.class);
        SkillServiceImpl service = service(skillMapper, storage);
        when(skillMapper.selectById(10L)).thenReturn(skillEntity());

        service.togglePublished(10L, false);

        assertThat(storage.readObjects).isEmpty();
        ArgumentCaptor<AiSkill> captor = ArgumentCaptor.forClass(AiSkill.class);
        verify(skillMapper).updateById(captor.capture());
        assertThat(captor.getValue().getPublished()).isFalse();
    }

    private SkillServiceImpl service(SkillMapper skillMapper, SkillStorageService storageService) {
        return service(skillMapper, storageService, "ai/skills");
    }

    private SkillServiceImpl service(SkillMapper skillMapper, SkillStorageService storageService, String rootPath) {
        SkillProperties properties = new SkillProperties();
        properties.setRootPath(rootPath);
        SkillServiceImpl service = new SkillServiceImpl(properties, storageService);
        ReflectionTestUtils.setField(service, "baseMapper", skillMapper);
        return service;
    }

    private static InMemorySkillStorage existingStorage() {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("ai/skills/platform-search/SKILL.md", skillMarkdown());
        storage.objects.put("ai/skills/platform-search/references/query-guide.md", "只返回启用菜单。");
        storage.objects.put("ai/skills/platform-search/scripts/build.py", "print('ignored')");
        storage.objects.put("ai/skills/platform-search/scripts/build.ps1", "Write-Host ignored");
        return storage;
    }

    private SkillMapper concurrentInsertMapper(ConcurrentSkillStorage storage) {
        SkillMapper mapper = mock(SkillMapper.class);
        when(mapper.selectCount(any())).thenReturn(0L);
        AtomicInteger order = new AtomicInteger();
        CyclicBarrier inserts = new CyclicBarrier(2);
        when(mapper.insert(any(AiSkill.class))).thenAnswer(invocation -> {
            AiSkill candidate = invocation.getArgument(0);
            int position = order.incrementAndGet();
            await(inserts);
            if (position == 1) {
                storage.winner.set(candidate);
                return 1;
            }
            throw new DuplicateKeyException("模拟数据库唯一约束冲突");
        });
        return mapper;
    }

    private static void runConcurrently(Runnable first, Runnable second) throws Exception {
        int failures = 0;
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            List<Future<?>> futures = List.of(executor.submit(first), executor.submit(second));
            for (Future<?> future : futures) {
                try {
                    future.get(5, SECONDS);
                } catch (ExecutionException failure) {
                    assertThat(failure.getCause()).isInstanceOf(DuplicateKeyException.class);
                    failures++;
                }
            }
        }
        assertThat(failures).isOne();
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, SECONDS);
        } catch (Exception failure) {
            throw new IllegalStateException("并发测试屏障等待失败", failure);
        }
    }

    private static InMemorySkillStorage versionedStorage() {
        InMemorySkillStorage storage = new InMemorySkillStorage();
        storage.objects.put("ai/skills/platform-search/1.0.0/SKILL.md", skillMarkdown());
        storage.objects.put("ai/skills/platform-search/1.0.0/references/query-guide.md", "只返回启用菜单。");
        storage.objects.put("ai/skills/platform-search/1.0.0/scripts/build.py", "print('ignored')");
        return storage;
    }

    private static SkillSaveReq saveReq() {
        SkillSaveReq req = new SkillSaveReq();
        req.setName("平台检索");
        req.setCode(" platform-search ");
        req.setDescription("检索平台菜单和权限");
        req.setCategory("system");
        req.setTags(List.of("system", "search", "system", " "));
        req.setVersion("1.0.0");
        req.setRequiresTools(List.of(" iam.search-user @ 1.0.0 ", "iam.search-user@1.0.0"));
        req.setRequiresToolsets(List.of(" module:iam:user-management ", "module:iam:user-management"));
        return req;
    }

    private static AiSkill skillEntity() {
        return AiSkill.builder()
                .id(10L)
                .name("平台检索")
                .code("platform-search")
                .description("检索平台菜单和权限")
                .category("system")
                .version("1.0.0")
                .requiresTools(List.of("iam.search-user@1.0.0"))
                .contentDigest(AgentSkillContent.sha256(skillMarkdown()))
                .skillPath("ai/skills/platform-search/")
                .skillFile("ai/skills/platform-search/SKILL.md")
                .resourceCount(1)
                .tags(List.of("system", "search"))
                .status(true)
                .published(true)
                .build();
    }

    private static AiSkill versionedSkillEntity(boolean published) {
        AiSkill skill = skillEntity();
        skill.setPublished(published);
        skill.setSkillPath("ai/skills/platform-search/1.0.0/");
        skill.setSkillFile("ai/skills/platform-search/1.0.0/SKILL.md");
        return skill;
    }

    private static List<MultipartFile> skillFiles() {
        return List.of(
                multipart("SKILL.md", skillMarkdown()),
                multipart("references/query-guide.md", "只返回启用菜单。"),
                multipart("scripts/build.py", "print('ignored')"));
    }

    private static List<MultipartFile> packageFiles(String markdown, String attachment) {
        return List.of(
                multipart("SKILL.md", markdown),
                multipart("references/query-guide.md", attachment),
                multipart("scripts/build.py", "print('complete')"));
    }

    private static List<String> skillRelativePaths() {
        return List.of("SKILL.md", "references/query-guide.md", "scripts/build.py");
    }

    private static MockMultipartFile multipart(String relativePath, String content) {
        return new MockMultipartFile("files", relativePath, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static String skillMarkdown() {
        return """
                ---
                name: platform-search
                description: 检索平台菜单和权限
                ---

                当用户需要查询平台菜单、按钮权限或路由时，先确认查询范围，再返回匹配结果。
                使用 references/query-guide.md 中的查询规范。
                """;
    }

    private static class InMemorySkillStorage implements SkillStorageService {

        private final Map<String, String> objects = new LinkedHashMap<>();
        private final List<String> writtenObjects = new java.util.ArrayList<>();
        private final List<String> deletedFolders = new java.util.ArrayList<>();
        private final List<String> readObjects = new java.util.ArrayList<>();
        private final List<String> downloadedFolders = new java.util.ArrayList<>();
        private int failUploadAt = -1;
        private int uploadAttempts;
        private RuntimeException readFailure;
        private boolean failDelete;

        @Override
        public void upload(String objectPath, MultipartFile file) {
            uploadAttempts++;
            if (uploadAttempts == failUploadAt) {
                throw new IllegalStateException("模拟上传失败: " + objectPath);
            }
            try {
                objects.put(objectPath, new String(file.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException ex) {
                throw new IllegalStateException(ex);
            }
        }

        @Override
        public String readText(String objectPath) {
            readObjects.add(objectPath);
            if (readFailure != null) {
                throw readFailure;
            }
            return objects.get(objectPath);
        }

        @Override
        public void writeText(String objectPath, String content) {
            writtenObjects.add(objectPath);
            objects.put(objectPath, content);
        }

        @Override
        public List<String> list(String folderPath) {
            return objects.keySet()
                    .stream()
                    .filter(key -> key.startsWith(folderPath))
                    .sorted()
                    .toList();
        }

        @Override
        public void deleteFolder(String folderPath) {
            if (failDelete) {
                throw new IllegalStateException("模拟对象目录删除失败: " + folderPath);
            }
            deletedFolders.add(folderPath);
            List<String> deletingKeys = objects.keySet()
                    .stream()
                    .filter(key -> key.startsWith(folderPath))
                    .toList();
            deletingKeys.forEach(objects::remove);
        }

        @Override
        public byte[] downloadFolder(String folderPath) {
            downloadedFolders.add(folderPath);
            return "skill zip bytes".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static final class ConcurrentSkillStorage implements SkillStorageService {

        private static final String SHARED_FOLDER = "ai/skills/platform-search/1.0.0/";
        private final Map<String, String> objects = new ConcurrentHashMap<>();
        private final List<String> deletedFolders = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
        private final AtomicReference<AiSkill> winner = new AtomicReference<>();
        private final CyclicBarrier sharedFolderChecks = new CyclicBarrier(2);

        @Override
        public void upload(String objectPath, MultipartFile file) {
            try {
                objects.put(objectPath, new String(file.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException failure) {
                throw new IllegalStateException("并发测试读取上传文件失败", failure);
            }
        }

        @Override
        public String readText(String objectPath) {
            return objects.get(objectPath);
        }

        @Override
        public void writeText(String objectPath, String content) {
            objects.put(objectPath, content);
        }

        @Override
        public List<String> list(String folderPath) {
            if (SHARED_FOLDER.equals(folderPath)) {
                await(sharedFolderChecks);
            }
            return objects.keySet().stream().filter(path -> path.startsWith(folderPath)).sorted().toList();
        }

        @Override
        public byte[] downloadFolder(String folderPath) {
            return new byte[0];
        }

        @Override
        public void deleteFolder(String folderPath) {
            deletedFolders.add(folderPath);
            objects.keySet().removeIf(path -> path.startsWith(folderPath));
        }
    }
}
