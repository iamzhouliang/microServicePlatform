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

import com.microservice.platform.ai.core.processor.DocumentProcessor;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import com.microservice.platform.ai.repository.KnowledgeChunkMapper;
import com.microservice.platform.ai.repository.KnowledgeItemMapper;
import com.microservice.platform.ai.service.GraphService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.KnowledgeChunkService;
import com.microservice.platform.ai.service.KnowledgeItemService;
import com.microservice.platform.ai.service.VectorService;
import com.microservice.platform.suite.feign.OssFileFeign;
import com.microservice.platform.suite.feign.domain.resp.OssFileResp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("知识条目服务")
class KnowledgeItemServiceImplTest {

    @Test
    @DisplayName("上传文档后保存 OSS 完整对象路径而不是目录路径")
    void uploadAndProcessStoresOssObjectPath() throws Exception {
        KnowledgeItemMapper knowledgeItemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeChunkService knowledgeChunkService = mock(KnowledgeChunkService.class);
        DocumentProcessor documentProcessor = mock(DocumentProcessor.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        OssFileFeign ossFileFeign = mock(OssFileFeign.class);
        ObjectProvider<VectorService> vectorServiceProvider = mock(ObjectProvider.class);
        ObjectProvider<GraphService> graphServiceProvider = mock(ObjectProvider.class);
        ObjectProvider<KnowledgeItemService> selfProvider = mock(ObjectProvider.class);
        KnowledgeItemServiceImpl service = new KnowledgeItemServiceImpl(
                mock(KnowledgeChunkMapper.class),
                knowledgeChunkService,
                documentProcessor,
                knowledgeBaseService,
                ossFileFeign,
                vectorServiceProvider,
                graphServiceProvider,
                selfProvider);
        ReflectionTestUtils.setField(service, "baseMapper", knowledgeItemMapper);
        when(selfProvider.getObject()).thenReturn(service);
        OssFileResp uploadResp = new OssFileResp();
        uploadResp.setPath("rag/docs/");
        uploadResp.setFilename("manual.md");
        uploadResp.setOriginalFilename("用户手册.md");
        uploadResp.setExt("md");
        uploadResp.setSize(128L);
        when(ossFileFeign.upload(any())).thenReturn(uploadResp);
        when(documentProcessor.extractText(any(File.class), any())).thenReturn("文档内容");
        when(knowledgeBaseService.getById(7L)).thenReturn(KnowledgeBase.builder().id(7L).build());
        doAnswer(invocation -> {
            KnowledgeItem item = invocation.getArgument(0);
            item.setId(100L);
            return 1;
        }).when(knowledgeItemMapper).insert(any(KnowledgeItem.class));
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "用户手册.md",
                "text/markdown",
                "文档内容".getBytes(StandardCharsets.UTF_8));

        Long id = service.uploadAndProcess(7L, file);

        assertThat(id).isEqualTo(100L);
        ArgumentCaptor<KnowledgeItem> captor = ArgumentCaptor.forClass(KnowledgeItem.class);
        verify(knowledgeItemMapper).insert(captor.capture());
        assertThat(captor.getValue().getFilePath()).isEqualTo("rag/docs/manual.md");
        assertThat(captor.getValue().getFilePath()).isNotEqualTo("rag/docs/");
    }
}
