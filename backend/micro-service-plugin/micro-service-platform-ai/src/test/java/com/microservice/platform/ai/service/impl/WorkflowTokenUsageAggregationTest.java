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

import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionResult;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionScope;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowExecution;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTokenUsageAggregationTest {

    @Test
    void aggregatesTokenUsageFromAllLlmNodes() {
        WorkflowExecutionScope scope = new WorkflowExecutionScope();
        scope.writeAll(Map.of(
                "nodes.llm-1.inputTokens", 100,
                "nodes.llm-1.outputTokens", 40,
                "nodes.classifier.inputTokens", 20L,
                "nodes.classifier.outputTokens", 5L,
                "nodes.code.output", "ignored"));
        WorkflowExecutionResult result = WorkflowExecutionResult.success(scope, Map.of(), List.of(), null);
        WorkflowExecution execution = new WorkflowExecution();

        WorkflowExecutionServiceImpl.applyTokenUsage(execution, result);

        assertThat(execution.getInputTokens()).isEqualTo(120L);
        assertThat(execution.getOutputTokens()).isEqualTo(45L);
        assertThat(execution.getTotalTokens()).isEqualTo(165L);
        assertThat(execution.getLlmCallCount()).isEqualTo(2);
    }
}
