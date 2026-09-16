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

package com.microservice.platform.ai.core.workflow.runtime;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutorRegistry;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.runtime.adapter.NodeExecutorAdapter;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowNodeAdapterTest {

    @Test
    void writesNodeExecutorOutputsToStableNodeScopeKeys() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(new StubExecutor(NodeType.LLM,
                NodeExecutionResult.success(Map.of("content", "hello", "totalTokens", 3)))));
        registry.init();
        NodeExecutorAdapter adapter = new NodeExecutorAdapter(registry);
        WorkflowExecutionScope scope = new WorkflowExecutionScope();
        ExecutionContext executionContext = ExecutionContext.builder().executionId("exec-1").build();
        CompiledWorkflow.CompiledNode node = new CompiledWorkflow.CompiledNode(
                "llm_1", NodeType.LLM, "Display label", Map.of("promptTemplate", "{{inputs.question}}"));

        adapter.execute(node, scope, executionContext);

        assertThat(scope.read("nodes.llm_1.content")).isEqualTo("hello");
        assertThat(scope.read("nodes.llm_1.totalTokens")).isEqualTo(3);
        assertThat(scope.read("nodes.llm_1.status")).isEqualTo("COMPLETED");
        assertThat(executionContext.getNodeOutput("llm_1", "content", String.class)).isEqualTo("hello");
    }

    @Test
    void failsFastWhenNodeExecutorFails() {
        NodeExecutorRegistry registry = new NodeExecutorRegistry(List.of(new StubExecutor(NodeType.TOOL,
                NodeExecutionResult.failure("tool unavailable"))));
        registry.init();
        NodeExecutorAdapter adapter = new NodeExecutorAdapter(registry);
        WorkflowExecutionScope scope = new WorkflowExecutionScope();
        CompiledWorkflow.CompiledNode node = new CompiledWorkflow.CompiledNode(
                "tool_1", NodeType.TOOL, "Tool", Map.of("task", "lookup"));

        assertThatThrownBy(() -> adapter.execute(node, scope, ExecutionContext.builder().executionId("exec-2").build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tool unavailable");
        assertThat(scope.read("nodes.tool_1.status")).isEqualTo("FAILED");
        assertThat(scope.read("nodes.tool_1.error")).isEqualTo("tool unavailable");
    }

    private static final class StubExecutor implements NodeExecutor {

        private final NodeType type;
        private final NodeExecutionResult result;

        private StubExecutor(NodeType type, NodeExecutionResult result) {
            this.type = type;
            this.result = result;
        }

        @Override
        public NodeType getType() {
            return type;
        }

        @Override
        public void validate(WorkflowNode node) {
        }

        @Override
        public NodeExecutionResult execute(
                                           WorkflowNode node, com.microservice.platform.ai.core.workflow.context.ExecutionContext context) {
            return result;
        }
    }
}
