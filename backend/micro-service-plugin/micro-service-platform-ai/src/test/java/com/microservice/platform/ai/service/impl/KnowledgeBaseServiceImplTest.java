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

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.platform.ai.domain.dto.req.KnowledgeBasePageReq;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.repository.KnowledgeBaseMapper;
import com.microservice.platform.ai.repository.KnowledgeItemMapper;
import com.microservice.platform.ai.service.KnowledgeChunkService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("知识库服务")
class KnowledgeBaseServiceImplTest {

    @Test
    @DisplayName("分组统计显式覆盖实体默认 ID 排序")
    void typeCountQueryOrdersOnlyByGroupedColumns() {
        KnowledgeBaseMapper baseMapper = mock(KnowledgeBaseMapper.class);
        KnowledgeItemMapper itemMapper = mock(KnowledgeItemMapper.class);
        KnowledgeBaseServiceImpl service = new KnowledgeBaseServiceImpl(
                itemMapper,
                mock(KnowledgeChunkService.class),
                mock(ObjectProvider.class),
                mock(ObjectProvider.class));
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);

        Page<KnowledgeBase> page = new Page<>(1, 10);
        page.setRecords(List.of(KnowledgeBase.builder().id(7L).build()));
        page.setTotal(1);
        when(baseMapper.selectPage(any(), any())).thenReturn(page);
        when(itemMapper.selectMaps(any())).thenReturn(List.of());

        service.pageList(new KnowledgeBasePageReq());

        @SuppressWarnings("rawtypes")
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(itemMapper).selectMaps(captor.capture());
        String sqlSegment = captor.getValue().getSqlSegment().replace("`", "").toLowerCase();
        assertThat(sqlSegment).contains("order by kb_id asc,type asc");
        assertThat(sqlSegment).doesNotContain("order by id");
    }
}
