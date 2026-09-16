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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.platform.ai.core.provider.scoring.ScoringModelService;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.dto.req.ModelSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ModelDetailResp;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.repository.ModelMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Model API key security")
class ModelServiceImplSecurityTest {

    @Test
    @DisplayName("masks API keys in model detail JSON")
    void masksApiKeyInModelDetailJson() throws Exception {
        ModelDetailResp response = new ModelDetailResp();
        response.setApiKey("secret-api-key-value");

        String json = new ObjectMapper().writeValueAsString(response);
        String serializedApiKey = new ObjectMapper().readTree(json).path("apiKey").asText();

        assertThat(serializedApiKey).matches("\\*+");
        assertThat(json).doesNotContain("secret-api-key-value");
    }

    @Test
    @DisplayName("keeps the stored API key when an edit submits a masked placeholder")
    void maskedApiKeyUpdateKeepsStoredSecret() {
        ModelMapper mapper = mock(ModelMapper.class);
        ScoringModelService scoringModelService = mock(ScoringModelService.class);
        VectorStoreFactory vectorStoreFactory = mock(VectorStoreFactory.class);
        ModelServiceImpl service = new ModelServiceImpl(scoringModelService, vectorStoreFactory);
        ReflectionTestUtils.setField(service, "baseMapper", mapper);
        when(mapper.selectById(3L)).thenReturn(ModelEntity.builder()
                .id(3L)
                .apiKey("stored-secret")
                .build());

        ModelSaveReq request = new ModelSaveReq();
        request.setProvider(AiProvider.DEEP_SEEK.getValue());
        request.setType(ModelType.TEXT);
        request.setName("deepseek-v4-pro");
        request.setApiKey("*******************");
        request.setBaseUrl("https://api.deepseek.com");
        request.setVariables(Map.of("max_tokens", 4096));

        service.modify(3L, request);

        ArgumentCaptor<ModelEntity> captor = ArgumentCaptor.forClass(ModelEntity.class);
        verify(mapper).updateById(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("stored-secret");
    }
}
