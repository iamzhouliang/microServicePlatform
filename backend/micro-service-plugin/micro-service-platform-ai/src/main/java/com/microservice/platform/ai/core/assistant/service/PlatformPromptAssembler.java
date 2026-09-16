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

package com.microservice.platform.ai.core.assistant.service;

import com.microservice.framework.ai.harness.prompt.PromptAssembler;
import com.microservice.framework.ai.harness.prompt.PromptLayers;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 为普通聊天和业务助手分别装配三层 Prompt。 */
@Component
public final class PlatformPromptAssembler implements PromptAssembler<PlatformPromptContext> {

    private static final String CHAT_STABLE = """
            你是 MicroService AI，一位自然、友善、真诚、专业的人工智能对话助手。
            请直接回应用户的实际问题，并结合会话上下文保持连贯交流。
            """;

    private static final String BUSINESS_STABLE = """
            你是 MicroService 平台内负责查询和变更业务数据的人工智能助手。
            当用户请求查询或变更平台业务数据时，先判断是否存在相关技能；需要时使用 activate_skill
            读取完整技能说明，然后按照技能说明调用工具。信息不足时用自然语言追问，不得猜测业务参数。
            用户查询当前业务数据时，必须在本轮调用对应的权威读取工具；历史工具结果仅用于理解上下文，
            不得使用历史工具结果代替本轮查询，也不得把历史结果当作当前事实直接回答。
            只有收到工具的成功结果后才能说明操作已完成。需要确认或审批时，用自然语言说明待执行事项，
            当前回复正常结束，等待用户下一条消息。审批通过不会自动执行；等待审批时应
            请用户在获知审批通过后再发送一条继续消息，不得声称系统将在后台自动完成。
            技能与工具仅用于内部执行，不得向用户提及技能激活、Skill 或 Tool 名称，也不得展示 operationId、
            治理规则、风险等级、审批记录、原始 JSON、内部异常或 Harness 结构。
            """;

    private static final String WEB_SEARCH_STABLE = """
            你已启用实时联网搜索，并且可以使用本次请求返回的搜索结果。
            1. 必须基于本次搜索结果回答时效性问题；已获得搜索结果时，不得声称无法访问互联网或无法浏览网页。
            2. 优先采用官方网站、官方文档、监管机构等第一方权威来源，不要编造事实、来源或网址。
            3. 用户指定来源、网站或域名时，只能使用符合该限制的搜索结果；证据不足时应明确说明，而不是使用其他来源补足。
               对“只依据官方网站”这类要求，先识别目标官方域名，并使用 site:目标官方域名 约束搜索；禁止使用或标注不符合来源限制的结果。
            4. 对冲突或无法确认的信息说明不确定性，并将事实与推测分开。
            5. 使用搜索结果中的来源时，在对应事实后使用 [数字] 角标标注，数字必须与搜索结果的来源编号一致。
            """;

    @Override
    public PromptLayers assemble(PlatformPromptContext context) {
        Objects.requireNonNull(context, "平台 Prompt 上下文不能为空");
        String stable = context.profile() == PlatformPromptProfile.CHAT ? CHAT_STABLE : BUSINESS_STABLE;
        if (context.profile() == PlatformPromptProfile.CHAT && context.webSearchEnabled()) {
            stable = stable.trim() + "\n\n" + webSearchStablePrompt();
        }
        String session = context.profile() == PlatformPromptProfile.BUSINESS
                && context.capabilityIndex() != null && !context.capabilityIndex().isBlank()
                        ? "【当前可按需激活的技能索引】\n" + context.capabilityIndex().trim()
                        : "";
        return new PromptLayers(stable, session,
                context.turnEphemeral() == null ? "" : context.turnEphemeral());
    }

    /**
     * 返回统一的联网搜索约束，供所有普通聊天运行时复用。
     *
     * @return 稳定的联网搜索约束
     */
    public String webSearchStablePrompt() {
        return WEB_SEARCH_STABLE.trim();
    }
}
