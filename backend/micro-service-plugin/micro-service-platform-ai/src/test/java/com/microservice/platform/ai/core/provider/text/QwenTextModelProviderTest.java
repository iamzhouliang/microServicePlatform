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

import com.microservice.framework.ai.core.model.SimpleModelConfig;
import com.microservice.framework.ai.core.provider.text.QwenTextModelProvider;
import dev.langchain4j.community.model.dashscope.QwenChatRequestParameters;
import dev.langchain4j.community.model.dashscope.QwenStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Qwen 文本模型提供者")
class QwenTextModelProviderTest {

    @Test
    @DisplayName("启用联网搜索时默认返回搜索来源")
    void webSearchEnablesSourceReferences() {
        QwenTextModelProvider provider = new QwenTextModelProvider();

        StreamingChatModel model = provider.createStreamingModel(SimpleModelConfig.builder()
                .apiKey("test-api-key")
                .name("qwen-plus")
                .enableWebSearch(true)
                .build());

        QwenChatRequestParameters parameters =
                ((QwenStreamingChatModel) model).defaultRequestParameters();
        assertThat(parameters.enableSearch()).isTrue();
        assertThat(parameters.searchOptions()).isNotNull();
        assertThat(parameters.searchOptions().enableSource()).isTrue();
        assertThat(parameters.searchOptions().enableCitation()).isTrue();
        assertThat(parameters.searchOptions().citationFormat()).isEqualTo("[ ]");
        assertThat(parameters.searchOptions().forcedSearch()).isTrue();
        assertThat(parameters.searchOptions().searchStrategy()).isEqualTo("max");
    }
}
