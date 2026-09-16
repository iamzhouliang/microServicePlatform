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

package com.microservice.platform.ai.core.provider.embedding;

import com.microservice.framework.ai.core.provider.embedding.EmbeddingModelFactory;
import com.microservice.framework.ai.core.provider.embedding.EmbeddingModelRegistry;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.model.embedding.EmbeddingModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 向量模型服务（业务层）
 *
 * @author xJh
 * @since 2025/10/12
 */
@Service
@RequiredArgsConstructor
public class EmbeddingModelService {

    private final EmbeddingModelRegistry registry;

    /**
     * 根据配置获取 EmbeddingModel 实例
     *
     * @param config 模型配置
     * @return EmbeddingModel 实例
     */
    public EmbeddingModel getModel(ModelEntity config) {
        EmbeddingModelFactory factory = registry.getFactory(config);
        if (factory == null) {
            throw CheckedException.badRequest("未找到支持的向量模型提供商: " + config.getProviderCode());
        }
        return factory.createModel(config);
    }
}
