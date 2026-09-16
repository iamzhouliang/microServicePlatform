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

package com.microservice.platform.ai.core.provider.scoring;

import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.model.jina.JinaScoringModel;
import dev.langchain4j.model.scoring.ScoringModel;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Jina 重排序模型提供者
 *
 * @author xJh
 * @since 2025/12/18
 */
@Component
public class JinaScoringModelProvider implements ScoringModelProvider {

    private static final String DEFAULT_MODEL = "jina-reranker-v2-base-multilingual";

    @Override
    public boolean supports(ModelEntity config) {
        String provider = config.getProvider() != null ? config.getProvider().getValue() : null;
        return "jina".equalsIgnoreCase(provider) || "jina-ai".equalsIgnoreCase(provider);
    }

    @Override
    public ScoringModel createModel(ModelEntity config) {
        String modelName = config.getName() != null ? config.getName() : DEFAULT_MODEL;

        JinaScoringModel.JinaScoringModelBuilder builder = JinaScoringModel.builder()
                .apiKey(config.getApiKey())
                .modelName(modelName);

        // 从 variables 读取额外配置
        Map<String, Object> variables = config.getVariables();
        if (variables != null) {
            if (variables.containsKey("timeout")) {
                builder.timeout(Duration.ofSeconds(((Number) variables.get("timeout")).longValue()));
            }
            if (variables.containsKey("maxRetries")) {
                builder.maxRetries(((Number) variables.get("maxRetries")).intValue());
            }
        }

        return builder.build();
    }

    @Override
    public String providerId() {
        return "jina";
    }
}
