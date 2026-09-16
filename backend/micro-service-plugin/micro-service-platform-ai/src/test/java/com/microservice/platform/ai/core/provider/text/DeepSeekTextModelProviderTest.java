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

import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.model.SimpleModelConfig;
import com.microservice.framework.ai.core.provider.text.DeepSeekTextModelProvider;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DeepSeek text model provider")
class DeepSeekTextModelProviderTest {

    @Test
    @DisplayName("supports the live deepseek-v4-pro reasoning model")
    void supportsDeepSeekV4Pro() {
        DeepSeekTextModelProvider provider = new DeepSeekTextModelProvider();
        SimpleModelConfig config = SimpleModelConfig.builder()
                .providerCode("deepseek")
                .name("deepseek-v4-pro")
                .apiKey("test-api-key")
                .baseUrl("https://api.deepseek.com")
                .returnThinking(true)
                .build();

        assertThat(provider.supports(config)).isTrue();
        assertThat(AiProvider.DEEP_SEEK.supportsDeepThinking(config.getName())).isTrue();
        assertThat(ReflectionTestUtils.getField(
                (OpenAiChatModel) provider.createModel(config), "returnThinking")).isEqualTo(true);
        assertThat(ReflectionTestUtils.getField(
                (OpenAiStreamingChatModel) provider.createStreamingModel(config), "returnThinking")).isEqualTo(true);
    }
}
