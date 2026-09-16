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

import java.util.Objects;
import java.util.stream.Stream;

/**
 * 按变化频率拆分的三层 Prompt。
 *
 * @param stable 稳定前缀层
 * @param sessionContext 会话上下文层
 * @param turnEphemeral 本轮易变层
 */
public record PromptLayers(String stable, String sessionContext, String turnEphemeral) {

    public PromptLayers {
        stable = Objects.requireNonNull(stable, "稳定 Prompt 不能为空");
        sessionContext = Objects.requireNonNull(sessionContext, "会话上下文 Prompt 不能为空");
        turnEphemeral = Objects.requireNonNull(turnEphemeral, "本轮易变 Prompt 不能为空");
        if (stable.isBlank()) {
            throw new IllegalArgumentException("稳定 Prompt 不能为空白");
        }
    }

    /** 在不改变稳定前缀的前提下追加会话上下文。 */
    public PromptLayers appendSessionContext(String context) {
        if (context == null || context.isBlank()) {
            return this;
        }
        String appended = sessionContext.isBlank() ? context.trim() : sessionContext.trim() + "\n\n" + context.trim();
        return new PromptLayers(stable, appended, turnEphemeral);
    }

    /** 按稳定层、会话上下文层、本轮易变层的固定顺序渲染。 */
    public String render() {
        return Stream.of(stable, sessionContext, turnEphemeral)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.joining("\n\n"));
    }
}
