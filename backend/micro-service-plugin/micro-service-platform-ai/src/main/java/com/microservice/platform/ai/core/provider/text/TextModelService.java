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

package com.microservice.platform.ai.core.provider.text;

import com.microservice.framework.ai.core.provider.text.TextModelProvider;
import com.microservice.framework.ai.core.provider.text.TextModelProviderRegistry;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 文本模型服务（业务层）
 *
 * @author xJh
 * @since 2025/10/11
 */
@Service
@RequiredArgsConstructor
public class TextModelService {

    private final TextModelCache cache;
    private final TextModelProviderRegistry registry;

    public ChatModel model(ModelEntity config) {
        return cache.getModel(config);
    }

    public StreamingChatModel streamModel(ModelEntity config) {
        return cache.getStreamModel(config);
    }

    public TextModelProvider getProvider(ModelEntity config) {
        return registry.getProvider(config);
    }

    public boolean isProviderAvailable(String providerId) {
        return registry.getAvailableProviderIds().contains(providerId.toLowerCase());
    }
}
