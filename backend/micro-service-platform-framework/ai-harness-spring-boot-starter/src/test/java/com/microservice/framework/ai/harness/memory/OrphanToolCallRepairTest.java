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

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrphanToolCallRepairTest {
    
    @Test
    void removesOnlyIncompleteToolCallChainAndOrphanResults() {
        ToolExecutionRequest complete = request("call-1");
        ToolExecutionRequest orphan = request("call-2");
        UserMessage before = UserMessage.from("开始");
        AiMessage completeCall = AiMessage.from(List.of(complete));
        ToolExecutionResultMessage completeResult = ToolExecutionResultMessage.from(complete, "完成");
        AiMessage incompleteCall = AiMessage.from(List.of(orphan));
        UserMessage after = UserMessage.from("继续");
        ToolExecutionResultMessage orphanResult = ToolExecutionResultMessage.from("unknown", "tool", "无主结果");
        List<ChatMessage> history = List.of(
                before, completeCall, completeResult, incompleteCall, after, orphanResult);
        
        assertThat(new OrphanToolCallRepair().repair(history))
                .containsExactly(before, completeCall, completeResult, after);
    }
    
    private static ToolExecutionRequest request(String id) {
        return ToolExecutionRequest.builder().id(id).name("tool").arguments("{}").build();
    }
}
