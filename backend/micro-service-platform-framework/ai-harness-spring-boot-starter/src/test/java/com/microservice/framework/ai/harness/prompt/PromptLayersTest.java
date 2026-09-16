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

package com.microservice.framework.ai.harness.prompt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PromptLayersTest {
    
    @Test
    void shouldRenderStableSessionAndTurnLayersInFixedOrder() {
        PromptLayers layers = new PromptLayers("稳定人格", "会话能力", "本轮待处理事项");
        
        assertThat(layers.render()).isEqualTo("稳定人格\n\n会话能力\n\n本轮待处理事项");
        assertThat(layers.appendSessionContext("会话摘要").render())
                .isEqualTo("稳定人格\n\n会话能力\n\n会话摘要\n\n本轮待处理事项");
    }
    
    @Test
    void shouldSkipBlankLayersWithoutProducingUnstableSeparators() {
        assertThat(new PromptLayers("稳定人格", "", " ").render()).isEqualTo("稳定人格");
    }
}
