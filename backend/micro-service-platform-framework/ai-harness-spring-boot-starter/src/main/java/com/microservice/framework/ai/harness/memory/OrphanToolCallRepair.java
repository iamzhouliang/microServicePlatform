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

package com.microservice.framework.ai.harness.memory;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 修复持久化历史中未配对的 LangChain4j Tool Call。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class OrphanToolCallRepair {
    
    /**
     * 仅删除不完整调用链和无主结果，其他消息保持原顺序。
     *
     * @param history 持久化的完整聊天历史
     * @return 修复后的连续消息序列
     */
    public List<ChatMessage> repair(List<ChatMessage> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatMessage> repaired = new ArrayList<>();
        int index = 0;
        while (index < history.size()) {
            ChatMessage message = Objects.requireNonNull(history.get(index), "聊天历史消息不能为空");
            if (message instanceof ToolExecutionResultMessage) {
                index++;
                continue;
            }
            if (!(message instanceof AiMessage aiMessage) || !aiMessage.hasToolExecutionRequests()) {
                repaired.add(message);
                index++;
                continue;
            }
            Set<String> expectedIds = new LinkedHashSet<>();
            aiMessage.toolExecutionRequests().forEach(request -> expectedIds.add(request.id()));
            List<ToolExecutionResultMessage> results = new ArrayList<>();
            int next = index + 1;
            while (next < history.size() && history.get(next)instanceof ToolExecutionResultMessage result) {
                results.add(result);
                next++;
            }
            Set<String> actualIds = new LinkedHashSet<>();
            results.forEach(result -> actualIds.add(result.id()));
            if (!expectedIds.isEmpty() && actualIds.containsAll(expectedIds)) {
                repaired.add(aiMessage);
                results.stream().filter(result -> expectedIds.contains(result.id())).forEach(repaired::add);
            }
            index = next;
        }
        return List.copyOf(repaired);
    }
}
