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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * 模型上下文动态预算器。
 *
 * <p>预算遵循先扣后算：先扣除输出预留和安全缓冲，再扣除系统提示、工具定义、
 * 当前输入与摘要，剩余空间才允许注入最近历史消息。</p>
 *
 * @author xiao1
 * @since 2026-07
 */
public final class ContextBudgetPlanner {
    
    /**
     * 计算可用历史预算。
     *
     * @param costs 各项上下文成本
     * @return 预算结果
     * @throws IllegalArgumentException 当固定成本已耗尽模型窗口时抛出
     */
    public ContextBudget plan(ContextCosts costs) {
        Objects.requireNonNull(costs, "上下文成本不能为空");
        validateNonNegative(costs);
        int effectiveWindowTokens = costs.contextWindowTokens()
                - costs.reservedOutputTokens()
                - costs.safetyBufferTokens();
        int fixedTokens = costs.systemPromptTokens()
                + costs.toolSchemaTokens()
                + costs.currentInputTokens();
        int historyTokens = effectiveWindowTokens - fixedTokens - costs.summaryTokens();
        if (effectiveWindowTokens <= 0 || historyTokens <= 0) {
            throw new IllegalArgumentException("上下文预算不足，系统提示、工具定义、当前输入或摘要已耗尽模型窗口");
        }
        return new ContextBudget(effectiveWindowTokens, fixedTokens, historyTokens);
    }
    
    /**
     * 从末尾选择预算内的最新元素，并保持原始顺序。
     *
     * @param items         原始元素
     * @param tokenCounter  元素 Token 计数器
     * @param maxItems      最大元素数量
     * @param maxTokens     最大 Token 数量
     * @param <T>           元素类型
     * @return 预算内的最新元素
     */
    public <T> List<T> selectRecent(List<T> items, ToIntFunction<T> tokenCounter, int maxItems, int maxTokens) {
        if (items == null || items.isEmpty() || maxItems <= 0 || maxTokens <= 0) {
            return List.of();
        }
        Objects.requireNonNull(tokenCounter, "Token 计数器不能为空");
        List<T> selected = new ArrayList<>();
        int usedTokens = 0;
        for (int index = items.size() - 1; index >= 0 && selected.size() < maxItems; index--) {
            T item = items.get(index);
            int itemTokens = Math.max(0, tokenCounter.applyAsInt(item));
            if (itemTokens > maxTokens - usedTokens) {
                // 历史必须保持连续后缀，不能跨过超大消息拼接语义不连续的旧上下文。
                break;
            }
            selected.add(item);
            usedTokens += itemTokens;
        }
        Collections.reverse(selected);
        return List.copyOf(selected);
    }
    
    private void validateNonNegative(ContextCosts costs) {
        if (costs.contextWindowTokens() <= 0
                || costs.reservedOutputTokens() < 0
                || costs.safetyBufferTokens() < 0
                || costs.systemPromptTokens() < 0
                || costs.toolSchemaTokens() < 0
                || costs.currentInputTokens() < 0
                || costs.summaryTokens() < 0) {
            throw new IllegalArgumentException("上下文预算参数不能为负数，模型窗口必须大于零");
        }
    }
    
    /**
     * 上下文各项成本。
     *
     * @param contextWindowTokens 模型上下文窗口大小
     * @param reservedOutputTokens 输出预留 Token
     * @param safetyBufferTokens 安全缓冲 Token
     * @param systemPromptTokens 系统提示 Token
     * @param toolSchemaTokens Tool Schema Token
     * @param currentInputTokens 当前输入 Token
     * @param summaryTokens 历史摘要 Token
     */
    public record ContextCosts(
                               int contextWindowTokens,
                               int reservedOutputTokens,
                               int safetyBufferTokens,
                               int systemPromptTokens,
                               int toolSchemaTokens,
                               int currentInputTokens,
                               int summaryTokens) {
    }
    
    /**
     * 动态预算结果。
     *
     * @param effectiveWindowTokens 扣除输出预留和安全缓冲后的窗口
     * @param fixedTokens 系统提示、Tool Schema 与当前输入成本
     * @param historyTokens 可用于历史消息的 Token
     */
    public record ContextBudget(int effectiveWindowTokens, int fixedTokens, int historyTokens) {
    }
}
