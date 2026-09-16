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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorization;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorizationProvider;
import com.microservice.framework.ai.harness.runtime.HarnessInvocation;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.provider.mcp.McpToolsetContributor;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.ToolNodeConfig;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import dev.langchain4j.invocation.InvocationParameters;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.tool.ToolProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 工具节点执行器
 * MCP 工具节点通过统一 Toolset Resolver 和 Harness 执行，不建立独立工具运行时。
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ToolNodeExecutor extends AbstractNodeExecutor {

    private final ToolsetResolver toolsetResolver;
    private final HarnessOperationCoordinator operationCoordinator;
    private final DelegatedAuthorizationProvider authorizationProvider;
    private final Environment environment;
    private final TextModelService textModelService;
    private final ModelConfigRetriever modelConfigRetriever;

    @Override
    public NodeType getType() {
        return NodeType.TOOL;
    }

    @Override
    public void validate(WorkflowNode node) {
        ToolNodeConfig config = parseConfig(node, ToolNodeConfig.class);
        if (config.getMcpServerId() == null) {
            throw new IllegalArgumentException("工具节点缺少 mcpServerId 配置");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行工具节点: {}", node.getId());

        ToolNodeConfig config = parseConfig(node, ToolNodeConfig.class);
        Long mcpServerId = config.getMcpServerId();

        // 获取任务描述
        String task = resolveTemplate(config.getTask(), context);
        if (task == null || task.isEmpty()) {
            return NodeExecutionResult.failure("工具节点任务描述为空");
        }

        // 获取模型配置（用于工具调用决策）
        Long modelId = config.getModelId();
        ModelEntity model;
        if (modelId != null) {
            model = modelConfigRetriever.getRequiredModel(modelId, ModelType.TEXT);
        } else {
            // 使用默认模型
            // 假设 ID 1 是默认模型
            model = modelConfigRetriever.getRequiredModel(1L);
        }

        try {
            String toolsetId = McpToolsetContributor.toolsetId(context.getTenantId(), mcpServerId);
            ToolProvider toolProvider = toolsetResolver.providerForRequirements(Set.of(toolsetId), Set.of(),
                    operationCoordinator);

            // 获取模型
            ChatModel chatModel = textModelService.model(model);

            // 创建带工具的 AI 服务
            WorkflowToolAssistant executor = AiServices.builder(WorkflowToolAssistant.class)
                    .chatModel(chatModel)
                    .toolProvider(toolProvider)
                    .build();

            // 执行工具调用
            String[] profiles = environment.getActiveProfiles();
            String activeEnvironment = profiles.length == 0 ? "default" : profiles[0];
            Set<String> permissions = context.getPermissions() == null ? Set.of() : Set.copyOf(context.getPermissions());
            HarnessInvocation invocation = new HarnessInvocation(context.getTenantId().toString(),
                    context.getUserId().toString(), context.getExecutionId(), UUID.randomUUID().toString(),
                    node.getId(), permissions, false);
            ToolsetSelectionContext selection = new ToolsetSelectionContext(invocation.tenantId(), invocation.userId(),
                    invocation.conversationId(), invocation.turnId(), "BUSINESS", "WORKFLOW", activeEnvironment,
                    permissions, Set.of(toolsetId), Set.of());
            InvocationParameters parameters = InvocationParameters.from(Map.of(
                    HarnessInvocation.PARAMETER_KEY, invocation,
                    ToolsetSelectionContext.PARAMETER_KEY, selection,
                    DelegatedAuthorization.PARAMETER_KEY, authorizationProvider.current()));
            String result = executor.execute(task, parameters);

            // 构建输出
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", result);
            outputs.put("task", task);
            outputs.put("mcpServerId", mcpServerId);
            if (config.getOutputVariable() != null && !config.getOutputVariable().isBlank()) {
                outputs.put(config.getOutputVariable(), result);
                context.setVariable(config.getOutputVariable(), result);
            }

            log.debug("工具节点 {} 执行完成，结果长度: {}", node.getId(), result.length());
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("工具节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("工具执行失败: " + e.getMessage());
        }
    }

    private interface WorkflowToolAssistant {

        String execute(@UserMessage String task, InvocationParameters parameters);
    }
}
