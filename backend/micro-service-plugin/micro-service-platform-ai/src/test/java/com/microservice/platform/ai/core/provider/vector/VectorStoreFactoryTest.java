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

package com.microservice.platform.ai.core.provider.vector;

import com.microservice.platform.ai.core.config.VectorStoreProperties;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Vector store factory")
class VectorStoreFactoryTest {

    @Test
    @DisplayName("uses the standard embedding dimensions model parameter")
    void usesStandardEmbeddingDimensionsParameter() {
        VectorStoreProperties properties = new VectorStoreProperties();
        properties.setType(VectorStoreProperties.StoreType.PGVECTOR);
        properties.getPgvector().setDimension(384);
        ModelEntity model = ModelEntity.builder()
                .id(2L)
                .variables(Map.of("dimensions", 1536))
                .build();

        Integer dimension = ReflectionTestUtils.invokeMethod(
                new VectorStoreFactory(properties), "getVectorDimension", model);

        assertThat(dimension).isEqualTo(1536);
    }
}
