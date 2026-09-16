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

package com.microservice.platform.ai.service.impl;

import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("规则会话摘要生成器")
class RuleBasedConversationSummaryGeneratorTest {

    @Test
    @DisplayName("摘要使用九段渐进结构并保留用户目标和当前状态")
    void producesNineSectionProgressiveSummary() {
        RuleBasedConversationSummaryGenerator generator = new RuleBasedConversationSummaryGenerator();
        List<ConversationTurn> turns = List.of(
                ConversationTurn.builder().role(MessageRole.USER).userInput("创建昵称为星桥的用户").build(),
                ConversationTurn.builder().role(MessageRole.ASSISTANT).modelOutput("用户已创建，编号为 731").build());

        String summary = generator.summarize("用户偏好中文回复", turns);

        assertThat(summary).contains(
                "## 用户目标", "## 关键实体", "## 已确认事实", "## 已完成动作", "## 当前状态",
                "## 待处理事项", "## 约束偏好", "## 失败风险", "## 近期上下文",
                "创建昵称为星桥的用户", "用户已创建，编号为 731", "用户偏好中文回复");
    }

    @Test
    @DisplayName("摘要超过上限时保留最新对话事实")
    void preservesNewestFactsWhenSummaryExceedsLimit() {
        RuleBasedConversationSummaryGenerator generator = new RuleBasedConversationSummaryGenerator();
        ConversationTurn newestTurn = ConversationTurn.builder()
                .role(MessageRole.USER)
                .userInput("最新事实：AURORA-731 临时称为星桥计划")
                .build();

        String summary = generator.summarize("旧摘要".repeat(1400), List.of(newestTurn));

        assertThat(summary)
                .hasSizeLessThanOrEqualTo(4000)
                .contains("最新事实：AURORA-731 临时称为星桥计划");
    }

    @Test
    @DisplayName("摘要忽略 Tool 请求、Tool 结果和内部思考原文")
    void excludesInternalToolLoopMessages() {
        RuleBasedConversationSummaryGenerator generator = new RuleBasedConversationSummaryGenerator();
        ConversationTurn request = ConversationTurn.builder().role(MessageRole.ASSISTANT)
                .modelOutput("内部工具参数 password=secret")
                .variables(Map.of(ConversationMessageServiceImpl.MESSAGE_KIND,
                        ConversationMessageServiceImpl.AI_TOOL_REQUEST))
                .build();
        ConversationTurn result = ConversationTurn.builder().role(MessageRole.TOOL)
                .modelOutput("内部技能正文 token=secret").build();

        String summary = generator.summarize(null, List.of(request, result));

        assertThat(summary).doesNotContain("password", "token", "secret", "内部技能正文");
    }
}
