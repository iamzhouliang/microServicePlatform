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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContextBudgetPlannerTest {
    
    @Test
    void calculatesHistoryBudgetAfterAllFixedCostsAndSummary() {
        ContextBudgetPlanner planner = new ContextBudgetPlanner();
        
        ContextBudgetPlanner.ContextBudget budget = planner.plan(
                new ContextBudgetPlanner.ContextCosts(32_000, 4_000, 2_000,
                        2_000, 5_000, 1_000, 3_000));
        
        assertThat(budget.effectiveWindowTokens()).isEqualTo(26_000);
        assertThat(budget.fixedTokens()).isEqualTo(8_000);
        assertThat(budget.historyTokens()).isEqualTo(15_000);
    }
    
    @Test
    void failsClosedWhenFixedCostsExhaustTheContextWindow() {
        ContextBudgetPlanner planner = new ContextBudgetPlanner();
        
        assertThatThrownBy(() -> planner.plan(new ContextBudgetPlanner.ContextCosts(
                8_000, 4_000, 2_000, 1_000, 2_000, 1_000, 1_000)))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("上下文预算不足");
    }
    
    @Test
    void selectsNewestItemsWithinMessageAndTokenLimitsWithoutReordering() {
        ContextBudgetPlanner planner = new ContextBudgetPlanner();
        List<String> selected = planner.selectRecent(List.of("old", "middle", "recent", "latest"),
                value -> value.length(), 3, 12);
        
        assertThat(selected).containsExactly("recent", "latest");
    }
    
    @Test
    void keepsSelectedHistoryAsAContiguousSuffix() {
        ContextBudgetPlanner planner = new ContextBudgetPlanner();
        
        List<String> selected = planner.selectRecent(
                List.of("old-small", "oversized-message", "latest"), String::length, 3, 10);
        
        assertThat(selected).containsExactly("latest");
    }
}
