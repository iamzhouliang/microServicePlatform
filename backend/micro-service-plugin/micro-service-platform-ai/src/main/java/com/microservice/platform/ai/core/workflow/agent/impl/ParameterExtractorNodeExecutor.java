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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.helper.ModelConfigRetriever;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.ParameterExtractorConfig;
import com.microservice.platform.ai.core.workflow.config.node.ParameterExtractorConfig.ExtractParameter;
import com.microservice.platform.ai.core.workflow.config.node.ParameterExtractorConfig.InferenceMode;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.core.workflow.enums.WorkflowValueType;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 参数提取器节点执行器
 * 从自然语言文本中提取结构化参数。
 * 支持两种推理模式：
 * - FUNCTION_CALL: 使用模型的 Function Call 能力（更精确）
 * - PROMPT_BASED: 基于提示词提取（适用范围更广）
 * 输出包含：
 * - 提取的各个参数
 * - __is_success: 是否成功提取所有必需参数
 * - __reason: 失败原因（如果有）
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ParameterExtractorNodeExecutor extends AbstractNodeExecutor {

    /**
     * JSON 序列化器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 对话记忆存储 (executionId -> nodeId -> messages)
     */
    private static final Map<String, Map<String, LinkedList<ChatMessage>>> MEMORY_STORE = new ConcurrentHashMap<>();

    /**
     * 默认系统提示词
     * @param context 执行上下文
     * @param modelEntity 模型配置
     * @param nodeId 节点标识
     * @param config 节点配置
     * @param inputText inputText 参数
     * @return 处理结果
     */
    private static final String DEFAULT_SYSTEM_PROMPT = """
            You are a parameter extraction assistant. Your task is to extract specific parameters from the user's input.

            Rules:
            1. Extract ONLY the parameters defined in the schema.
            2. Return a valid JSON object with the extracted parameters.
            3. If a parameter cannot be found, use null for optional parameters or indicate it's missing for required ones.
            4. Do not include any explanation or additional text, only the JSON object.
            5. Ensure the extracted values match the expected types.
            """;

    private final TextModelService textModelService;
    private final ModelConfigRetriever modelConfigRetriever;

    @Override
    public NodeType getType() {
        return NodeType.PARAMETER_EXTRACTOR;
    }

    @Override
    public void validate(WorkflowNode node) {
        ParameterExtractorConfig config = parseConfig(node, ParameterExtractorConfig.class);

        if (config.getModelId() == null) {
            throw new IllegalArgumentException("参数提取器节点缺少 modelId 配置");
        }

        if (config.getParameters() == null || config.getParameters().isEmpty()) {
            throw new IllegalArgumentException("参数提取器节点至少需要一个参数定义");
        }

        // 验证每个参数定义
        for (int i = 0; i < config.getParameters().size(); i++) {
            ExtractParameter param = config.getParameters().get(i);
            if (param.getName() == null || param.getName().trim().isEmpty()) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个参数缺少名称");
            }
            if (param.getType() == null) {
                throw new IllegalArgumentException("第 " + (i + 1) + " 个参数缺少类型");
            }
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 PARAMETER_EXTRACTOR 节点: {}", node.getId());

        // 解析强类型配置
        ParameterExtractorConfig config = parseConfig(node, ParameterExtractorConfig.class);

        // 获取输入文本
        String inputVariable = config.getInputVariable();
        String inputText;
        if (inputVariable != null && !inputVariable.isEmpty()) {
            inputText = resolveTemplate(inputVariable, context);
        } else {
            inputText = context.getVariable("input", String.class);
        }

        if (inputText == null || inputText.trim().isEmpty()) {
            return createFailureResult("输入文本为空", config.getParameters());
        }

        // 获取模型
        ModelEntity modelEntity = modelConfigRetriever.getRequiredModel(config.getModelId());

        // 根据推理模式执行提取
        Map<String, Object> extractedParams;
        InferenceMode mode = config.getInferenceMode();
        if (mode == null) {
            // 默认使用提示词模式
            mode = InferenceMode.PROMPT_BASED;
        }

        try {
            if (mode == InferenceMode.FUNCTION_CALL) {
                extractedParams = extractWithFunctionCall(inputText, config, modelEntity, context, node.getId());
            } else {
                extractedParams = extractWithPrompt(inputText, config, modelEntity, context, node.getId());
            }
        } catch (Exception e) {
            log.error("参数提取节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return createFailureResult("参数提取失败: " + e.getMessage(), config.getParameters());
        }

        // 验证必需参数
        List<String> missingRequired = new ArrayList<>();
        for (ExtractParameter param : config.getParameters()) {
            if (param.isRequired()) {
                Object value = extractedParams.get(param.getName());
                if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                    missingRequired.add(param.getName());
                }
            }
        }

        // 构建输出
        Map<String, Object> outputs = new HashMap<>(extractedParams);

        // 添加状态变量
        boolean isSuccess = missingRequired.isEmpty();
        String reason = isSuccess ? "" : "缺少必填参数: " + String.join(", ", missingRequired);
        if (!isSuccess) {
            return NodeExecutionResult.failure(reason);
        }

        outputs.put("__is_success", isSuccess);
        outputs.put("__reason", reason);
        outputs.put("__extracted_count", extractedParams.size());
        outputs.put("__input_text", inputText);
        outputs.put("__inference_mode", mode.getCode());

        log.debug("参数提取器节点 {} 执行完成，提取参数数={}，成功={}",
                node.getId(), extractedParams.size(), isSuccess);

        return NodeExecutionResult.success(outputs);
    }

    /**
     * 使用 Function Call 模式提取参数
     * 注意：这需要模型支持 Function Calling
     * @param context 执行上下文
     * @param modelEntity 模型配置
     * @param nodeId 节点标识
     * @param config 节点配置
     * @param inputText inputText 参数
     * @return 处理结果
     */
    private Map<String, Object> extractWithFunctionCall(String inputText, ParameterExtractorConfig config,
                                                        ModelEntity modelEntity, ExecutionContext context, String nodeId) {
        // 构建 JSON Schema
        String jsonSchema = buildJsonSchema(config.getParameters());

        // 构建提示词
        String systemPrompt = DEFAULT_SYSTEM_PROMPT + "\n\nExpected output schema:\n```json\n" + jsonSchema + "\n```";

        // 构建消息
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        // 加载记忆（如果启用）
        if (config.isMemoryEnabled()) {
            List<ChatMessage> history = loadMemory(context.getExecutionId(), nodeId, config.getMemoryWindowSize());
            messages.addAll(history);
        }

        // 添加用户消息
        String userPrompt = buildExtractionPrompt(inputText, config);
        messages.add(UserMessage.from(userPrompt));

        // 调用模型
        ChatModel chatModel = textModelService.model(modelEntity);
        ChatResponse response = chatModel.chat(messages);

        String responseText = response.aiMessage().text();

        // 保存到记忆
        if (config.isMemoryEnabled()) {
            saveToMemory(context.getExecutionId(), nodeId,
                    UserMessage.from(userPrompt), response.aiMessage(), config.getMemoryWindowSize());
        }

        // 解析响应
        return parseExtractionResponse(responseText, config.getParameters());
    }

    /**
     * 使用提示词模式提取参数
     * @param context 执行上下文
     * @param modelEntity 模型配置
     * @param nodeId 节点标识
     * @param config 节点配置
     * @param inputText inputText 参数
     * @return 处理结果
     */
    private Map<String, Object> extractWithPrompt(String inputText, ParameterExtractorConfig config,
                                                  ModelEntity modelEntity, ExecutionContext context, String nodeId) {
        // 构建 JSON Schema
        String jsonSchema = buildJsonSchema(config.getParameters());

        // 构建系统提示词
        String systemPrompt = DEFAULT_SYSTEM_PROMPT + "\n\nExpected output schema:\n```json\n" + jsonSchema + "\n```";

        // 构建消息
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(SystemMessage.from(systemPrompt));

        // 加载记忆（如果启用）
        if (config.isMemoryEnabled()) {
            List<ChatMessage> history = loadMemory(context.getExecutionId(), nodeId, config.getMemoryWindowSize());
            messages.addAll(history);
        }

        // 构建用户提示词
        String userPrompt = buildExtractionPrompt(inputText, config);
        messages.add(UserMessage.from(userPrompt));

        // 调用模型
        ChatModel chatModel = textModelService.model(modelEntity);
        ChatResponse response = chatModel.chat(messages);

        String responseText = response.aiMessage().text();

        // 保存到记忆
        if (config.isMemoryEnabled()) {
            saveToMemory(context.getExecutionId(), nodeId,
                    UserMessage.from(userPrompt), response.aiMessage(), config.getMemoryWindowSize());
        }

        // 解析响应
        return parseExtractionResponse(responseText, config.getParameters());
    }

    /**
     * 构建 JSON Schema
     * @param parameters parameters 参数
     * @return 处理结果
     */
    private String buildJsonSchema(List<ExtractParameter> parameters) {
        Map<String, Object> schema = new HashMap<>();
        schema.put("type", "object");

        Map<String, Object> properties = new HashMap<>();
        List<String> required = new ArrayList<>();

        for (ExtractParameter param : parameters) {
            Map<String, Object> propSchema = new HashMap<>();
            propSchema.put("type", mapParameterType(param.getType()));

            if (param.getDescription() != null) {
                propSchema.put("description", param.getDescription());
            }

            if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                propSchema.put("enum", param.getEnumValues());
            }

            properties.put(param.getName(), propSchema);

            if (param.isRequired()) {
                required.add(param.getName());
            }
        }

        schema.put("properties", properties);
        if (!required.isEmpty()) {
            schema.put("required", required);
        }

        try {
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            log.warn("序列化 JSON Schema 失败: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 映射参数类型到 JSON Schema 类型
     * @param type 类型
     * @return 处理结果
     */
    private String mapParameterType(WorkflowValueType type) {
        return switch (type) {
            case STRING -> "string";
            case NUMBER -> "number";
            case BOOLEAN -> "boolean";
            case ARRAY -> "array";
            case OBJECT -> "object";
            case ANY -> "string";
        };
    }

    /**
     * 构建提取提示词
     * @param config 节点配置
     * @param inputText inputText 参数
     * @return 处理结果
     */
    private String buildExtractionPrompt(String inputText, ParameterExtractorConfig config) {
        StringBuilder sb = new StringBuilder();

        sb.append("Extract the following parameters from the text below:\n\n");

        // 列出参数
        sb.append("Parameters to extract:\n");
        for (ExtractParameter param : config.getParameters()) {
            sb.append("- ").append(param.getName());
            sb.append(" (").append(param.getType().getCode()).append(")");
            if (param.isRequired()) {
                sb.append(" [REQUIRED]");
            }
            if (param.getDescription() != null) {
                sb.append(": ").append(param.getDescription());
            }
            if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                sb.append(" (allowed values: ").append(String.join(", ", param.getEnumValues())).append(")");
            }
            sb.append("\n");
        }

        // 添加额外指导
        if (config.getInstructions() != null && !config.getInstructions().isEmpty()) {
            sb.append("\nAdditional instructions:\n");
            sb.append(config.getInstructions());
            sb.append("\n");
        }

        sb.append("\n---\nText to extract from:\n");
        sb.append(inputText);
        sb.append("\n---\n");
        sb.append("\nRespond with ONLY a valid JSON object containing the extracted parameters:");

        return sb.toString();
    }

    /**
     * 解析提取响应
     * @param parameters parameters 参数
     * @param response response 参数
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private Map<String, Object> parseExtractionResponse(String response, List<ExtractParameter> parameters) {
        Map<String, Object> result = new HashMap<>();

        if (response == null || response.isEmpty()) {
            return result;
        }

        try {
            // 尝试提取 JSON
            String jsonContent = extractJsonContent(response);
            JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonContent);

            // 提取每个参数
            for (ExtractParameter param : parameters) {
                JsonNode valueNode = jsonNode.get(param.getName());
                if (valueNode != null && !valueNode.isNull()) {
                    Object value = convertJsonValue(valueNode, param.getType());
                    result.put(param.getName(), value);
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("参数提取响应不是有效 JSON: " + e.getMessage(), e);
        }

        return result;
    }

    /**
     * 从响应中提取 JSON 内容
     * @param content 内容
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private String extractJsonContent(String content) {
        String trimmed = content.trim();

        // 如果已经是 JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        // 从 markdown 代码块提取
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = trimmed.indexOf("\n", jsonStart) + 1;
            int contentEnd = trimmed.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return trimmed.substring(contentStart, contentEnd).trim();
            }
        }

        // 从普通代码块提取
        int codeStart = trimmed.indexOf("```");
        if (codeStart >= 0) {
            int contentStart = trimmed.indexOf("\n", codeStart) + 1;
            int contentEnd = trimmed.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                String extracted = trimmed.substring(contentStart, contentEnd).trim();
                if (extracted.startsWith("{") || extracted.startsWith("[")) {
                    return extracted;
                }
            }
        }

        // 查找第一个 { 和最后一个 }
        int braceStart = trimmed.indexOf("{");
        int braceEnd = trimmed.lastIndexOf("}");
        if (braceStart >= 0 && braceEnd > braceStart) {
            return trimmed.substring(braceStart, braceEnd + 1);
        }

        throw new IllegalArgumentException("响应中未找到有效 JSON");
    }

    /**
     * 转换 JSON 值到对应类型
     * @param node 工作流节点
     * @param type 类型
     * @return 处理结果
     */
    private Object convertJsonValue(JsonNode node, WorkflowValueType type) {
        return switch (type) {
            case STRING -> node.asText();
            case NUMBER -> node.isInt() ? node.asInt() : node.asDouble();
            case BOOLEAN -> node.asBoolean();
            case ARRAY -> OBJECT_MAPPER.convertValue(node, List.class);
            case OBJECT -> OBJECT_MAPPER.convertValue(node, Map.class);
            case ANY -> OBJECT_MAPPER.convertValue(node, Object.class);
        };
    }

    /**
     * 创建失败结果
     * @param parameters parameters 参数
     * @param reason reason 参数
     * @return 处理结果
     */
    private NodeExecutionResult createFailureResult(String reason, List<ExtractParameter> parameters) {
        return NodeExecutionResult.failure(reason);
    }

    /**
     * 加载对话记忆
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param windowSize windowSize 参数
     * @return 处理结果
     */
    private List<ChatMessage> loadMemory(String executionId, String nodeId, Integer windowSize) {
        Map<String, LinkedList<ChatMessage>> nodeMemories = MEMORY_STORE.get(executionId);
        if (nodeMemories == null) {
            return new ArrayList<>();
        }

        LinkedList<ChatMessage> memory = nodeMemories.get(nodeId);
        if (memory == null || memory.isEmpty()) {
            return new ArrayList<>();
        }

        int maxMessages = (windowSize != null ? windowSize : 5) * 2;
        if (memory.size() <= maxMessages) {
            return new ArrayList<>(memory);
        }

        return new ArrayList<>(memory.subList(memory.size() - maxMessages, memory.size()));
    }

    /**
     * 保存对话到记忆
     * @param aiMessage aiMessage 参数
     * @param windowSize windowSize 参数
     * @param executionId 执行标识
     * @param nodeId 节点标识
     * @param userMessage userMessage 参数
     */
    private void saveToMemory(String executionId, String nodeId, UserMessage userMessage,
                              AiMessage aiMessage, Integer windowSize) {
        Map<String, LinkedList<ChatMessage>> nodeMemories =
                MEMORY_STORE.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
        LinkedList<ChatMessage> memory = nodeMemories.computeIfAbsent(nodeId, k -> new LinkedList<>());

        memory.add(userMessage);
        memory.add(aiMessage);

        int maxMessages = (windowSize != null ? windowSize : 5) * 2;
        while (memory.size() > maxMessages) {
            memory.removeFirst();
        }
    }

    /**
     * 清理执行记忆
     * @param executionId 执行标识
     */
    public static void clearMemory(String executionId) {
        MEMORY_STORE.remove(executionId);
    }

    /**
     * 获取参数名称列表
     * @param config 节点配置
     * @return 处理结果
     */
    public List<String> getParameterNames(ParameterExtractorConfig config) {
        if (config == null || config.getParameters() == null) {
            return new ArrayList<>();
        }
        return config.getParameters().stream()
                .map(ExtractParameter::getName)
                .collect(Collectors.toList());
    }
}
