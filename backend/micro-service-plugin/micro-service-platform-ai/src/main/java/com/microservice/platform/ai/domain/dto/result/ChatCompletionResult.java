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

package com.microservice.platform.ai.domain.dto.result;

import java.util.List;

/**
 * 助手对话完成结果
 * 替代此前在 SSE 完成回调中用无类型 {@code Map<String, Object>} + 强制类型转换（(String)/(Integer)）传递的写法，
 * 明确 content / inputTokens / outputTokens 的语义与类型。
 *
 * @param content      助手完整回复内容
 * @param thinking     模型思考内容
 * @param references   回答引用来源
 * @param inputTokens  提示词 token 数
 * @param outputTokens 补全 token 数
 */
public record ChatCompletionResult(String content, String thinking, List<ChatReference> references,
                                   Integer inputTokens, Integer outputTokens) {

    public ChatCompletionResult {
        references = references == null ? List.of() : List.copyOf(references);
    }

    public ChatCompletionResult(String content, Integer inputTokens, Integer outputTokens) {
        this(content, null, List.of(), inputTokens, outputTokens);
    }
}
