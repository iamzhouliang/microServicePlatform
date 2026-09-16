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

package com.microservice.platform.ai.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 会话上下文配置.
 *
 * @author xiao1
 * @since 2026-06
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.conversation.context")
public class ConversationContextProperties {

    /**
     * 普通对话最近原文轮数.
     */
    private int recentRounds = 10;

    /**
     * RAG 对话最近原文轮数.
     */
    private int ragRecentRounds = 8;

    /**
     * 智能体对话最近原文轮数.
     */
    private int agentRecentRounds = 10;

    /**
     * 摘要配置.
     */
    private Summary summary = new Summary();

    /**
     * 动态 Token 预算配置。
     */
    private Budget budget = new Budget();

    public int recentMessages() {
        return roundsToMessages(recentRounds);
    }

    public int ragRecentMessages() {
        return roundsToMessages(ragRecentRounds);
    }

    public int agentRecentMessages() {
        return roundsToMessages(agentRecentRounds);
    }

    private int roundsToMessages(int rounds) {
        return Math.max(1, rounds) * 2;
    }

    @Data
    public static class Summary {

        /**
         * 是否启用会话摘要.
         */
        private boolean enabled = true;

        /**
         * 会话至少新增多少条消息后才开始摘要.
         */
        private int minMessages = 30;

        /**
         * 距离上次摘要新增多少条消息后滚动更新.
         */
        private int incrementalMessages = 10;

        /**
         * 单次摘要最多读取多少条未覆盖消息.
         */
        private int maxSourceMessages = 100;
    }

    /**
     * 模型上下文预算。
     * <p>模型配置中的 {@code context_window_tokens} 和 {@code max_tokens} 可以覆盖窗口与输出预留。</p>
     */
    @Data
    public static class Budget {

        /** 是否启用动态 Token 预算。 */
        private boolean enabled = true;

        /** 默认模型上下文窗口。 */
        private int contextWindowTokens = 32_768;

        /** 默认输出预留。 */
        private int reservedOutputTokens = 4_096;

        /** 防止近似估算偏差的安全缓冲。 */
        private int safetyBufferTokens = 2_048;

        /** 普通对话系统提示预估。 */
        private int normalSystemPromptTokens = 1_024;

        /** 知识库对话系统提示预估。 */
        private int ragSystemPromptTokens = 2_048;

        /** 为 RAG 检索结果保留的预算。 */
        private int ragRetrievalReserveTokens = 8_192;

        /** Agent 人格和约束提示预估。 */
        private int agentSystemPromptTokens = 2_048;

        /** Agent Tool Schema 和 Skill 元数据预估。 */
        private int agentToolSchemaTokens = 4_096;
    }
}
