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

package com.microservice.platform.ai.core.workflow.runtime.adapter;

import com.microservice.platform.ai.core.workflow.agent.NodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutorRegistry;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionScope;
import com.microservice.platform.ai.core.workflow.runtime.CompiledWorkflow;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 节点执行器适配器，用于承接 LangChain4j workflow runtime 入口。
 *
 * @author xJh
 * @since 2026/05/24
 */
@Component
@RequiredArgsConstructor
public class NodeExecutorAdapter implements WorkflowNodeAdapter {

    private final NodeExecutorRegistry nodeExecutorRegistry;

    @Override
    public Result execute(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope,
                          ExecutionContext executionContext) {
        NodeExecutor executor = nodeExecutorRegistry.getExecutor(node.type());
        WorkflowNode workflowNode = WorkflowNode.builder()
                .id(node.id())
                .type(node.type())
                .label(node.displayName())
                .data(new HashMap<>(node.config()))
                .build();
        ExecutionContext context = buildContext(scope, executionContext);
        NodeExecutionResult result = executor.execute(workflowNode, context);
        if (!result.isSuccess()) {
            String message = result.getErrorMessage() == null ? "工作流节点执行失败: " + node.id() : result.getErrorMessage();
            scope.write(node.scopeKey("status"), "FAILED");
            scope.write(node.scopeKey("error"), message);
            throw new IllegalStateException(message);
        }
        Map<String, Object> outputs = result.getOutputs() == null ? Map.of() : result.getOutputs();
        outputs.forEach((key, value) -> scope.write(node.scopeKey(key), value));
        context.setNodeOutput(node.id(), outputs);
        if (result.getDuration() != null) {
            scope.write(node.scopeKey("duration"), result.getDuration());
        }
        if (result.getNextBranch() != null) {
            scope.write(node.scopeKey("selectedBranch"), result.getNextBranch());
            scope.write(node.scopeKey("status"), "COMPLETED");
            return Result.branch(result.getNextBranch());
        }
        if (result.getContinueLoop() != null) {
            scope.write(node.scopeKey("continueLoop"), result.getContinueLoop());
            scope.write(node.scopeKey("status"), "COMPLETED");
            return Result.loop(result.getContinueLoop());
        }
        scope.write(node.scopeKey("status"), "COMPLETED");
        return Result.completed();
    }

    private ExecutionContext buildContext(WorkflowExecutionScope scope, ExecutionContext executionContext) {
        ExecutionContext context = executionContext == null ? ExecutionContext.builder().build() : executionContext;
        for (Map.Entry<String, Object> entry : scope.state().entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        return context;
    }
}
