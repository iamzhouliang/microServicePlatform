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

import com.microservice.platform.ai.core.enums.KnowledgeItemStatus;
import com.microservice.platform.ai.core.processor.VectorizationProcessor;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("向量化编排状态机")
class VectorizationOrchestrationServiceTest {

    @Test
    @DisplayName("没有分片时向量化失败并将知识条目标记为 FAILED")
    void emptyChunksFailAndPersistItemFailure() {
        Fixture fixture = fixture();
        KnowledgeItem item = KnowledgeItem.builder().id(10L).kbId(20L)
                .status(KnowledgeItemStatus.PROCESSING).build();
        when(fixture.knowledgeItemService.getById(10L)).thenReturn(item);
        when(fixture.knowledgeBaseService.getById(20L))
                .thenReturn(KnowledgeBase.builder().id(20L).embedModelId(30L).enableGraph(false).build());
        when(fixture.modelService.getById(30L)).thenReturn(ModelEntity.builder().id(30L).build());
        when(fixture.knowledgeChunkService.listByItemId(10L)).thenReturn(List.of());

        assertThatThrownBy(() -> fixture.service.vectorizeKnowledgeItem(10L))
                .hasMessageContaining("没有可向量化的分片");

        verify(fixture.vectorizationStatusService).markFailed(10L);
    }

    @Test
    @DisplayName("FAILED 状态使用独立事务提交")
    void failureStatusUsesRequiresNewTransaction() throws Exception {
        Transactional transactional = VectorizationStatusService.class
                .getMethod("markFailed", Long.class)
                .getAnnotation(Transactional.class);
        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
    }

    private static Fixture fixture() {
        VectorizationProcessor vectorizationProcessor = mock(VectorizationProcessor.class);
        KnowledgeChunkService knowledgeChunkService = mock(KnowledgeChunkService.class);
        KnowledgeItemService knowledgeItemService = mock(KnowledgeItemService.class);
        KnowledgeBaseService knowledgeBaseService = mock(KnowledgeBaseService.class);
        ModelService modelService = mock(ModelService.class);
        VectorStoreFactory vectorStoreFactory = mock(VectorStoreFactory.class);
        VectorMetadataService vectorMetadataService = mock(VectorMetadataService.class);
        GraphExtractionService graphExtractionService = mock(GraphExtractionService.class);
        VectorizationStatusService vectorizationStatusService = mock(VectorizationStatusService.class);
        VectorizationOrchestrationService service = new VectorizationOrchestrationService(
                vectorizationProcessor,
                knowledgeChunkService,
                knowledgeItemService,
                knowledgeBaseService,
                modelService,
                vectorStoreFactory,
                vectorMetadataService,
                graphExtractionService,
                vectorizationStatusService);
        return new Fixture(service, knowledgeChunkService, knowledgeItemService, knowledgeBaseService, modelService,
                vectorizationStatusService);
    }

    private record Fixture(
                           VectorizationOrchestrationService service,
                           KnowledgeChunkService knowledgeChunkService,
                           KnowledgeItemService knowledgeItemService,
                           KnowledgeBaseService knowledgeBaseService,
                           ModelService modelService,
                           VectorizationStatusService vectorizationStatusService) {
    }
}
