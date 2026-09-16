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
import dev.langchain4j.model.scoring.ScoringModel;

/**
 * 重排序模型提供者接口
 * 定义重排序模型的通用抽象，支持多种重排序服务（Jina、Cohere、OpenAI 等）
 *
 * @author xJh
 * @since 2025/12/18
 */
public interface ScoringModelProvider {

    /**
     * 是否支持该配置
     *
     * @param config 模型配置
     * @return 是否支持
     */
    boolean supports(ModelEntity config);

    /**
     * 创建重排序模型实例
     *
     * @param config 模型配置
     * @return ScoringModel 实例
     */
    ScoringModel createModel(ModelEntity config);

    /**
     * 获取提供商标识
     *
     * @return 提供商 ID
     */
    String providerId();
}
