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
import com.microservice.platform.ai.core.workflow.config.node.LLMNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.LLMNodeConfig.ContextVariable;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import dev.langchain4j.data.image.Image;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * LLM 节点执行器
 * 复用现有的 TextModelService 执行大模型调用
 * 支持 Vision（图像理解）、Memory（对话记忆）、结构化输出等 Dify 增强功能
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LLMNodeExecutor extends AbstractNodeExecutor {

    /**
     * JSON 序列化器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 对话记忆存储 (executionId -> nodeId -> messages)
     * 用于在同一执行中保持对话上下文
     * @param config 节点配置
     * @param context 执行上下文
     * @param textPrompt textPrompt 参数
     * @return 处理结果
     */
    private static final Map<String, Map<String, LinkedList<ChatMessage>>> MEMORY_STORE = new ConcurrentHashMap<>();

    private final TextModelService textModelService;
    private final ModelConfigRetriever modelConfigRetriever;

    @Override
    public NodeType getType() {
        return NodeType.LLM;
    }

    @Override
    public void validate(WorkflowNode node) {
        LLMNodeConfig config = parseConfig(node, LLMNodeConfig.class);
        if (config.getModelId() == null) {
            throw new IllegalArgumentException("LLM 节点缺少 modelId 配置");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 LLM 节点: {}", node.getId());

        // 解析强类型配置
        LLMNodeConfig config = parseConfig(node, LLMNodeConfig.class);

        Long modelId = config.getModelId();
        if (modelId == null) {
            return NodeExecutionResult.failure("LLM 节点缺少 modelId 配置");
        }
        final ModelEntity modelEntity = modelConfigRetriever.getRequiredModel(modelId);

        String systemPrompt = config.getSystemPrompt();
        String promptTemplate = config.getPromptTemplate();
        final Double temperature = config.getTemperature() != null ? config.getTemperature() : 0.7;
        final Integer maxTokens = config.getMaxTokens();

        // 处理上下文变量
        Map<String, Object> contextVars = new HashMap<>(context.getAllVariables());
        if (config.getContextVariables() != null) {
            for (ContextVariable cv : config.getContextVariables()) {
                if (cv.getReference() != null) {
                    String resolvedValue = resolveTemplate(cv.getReference(), context);
                    contextVars.put(cv.getName(), resolvedValue);
                }
            }
        }

        // 解析模板中的变量
        String resolvedSystemPrompt = resolveTemplate(systemPrompt, context);
        final String resolvedUserPrompt = resolveTemplate(promptTemplate, context);

        // 构建系统提示词（包含结构化输出指令）
        if (config.getStructuredOutput() != null && config.getStructuredOutput().isEnabled()) {
            resolvedSystemPrompt = buildStructuredOutputPrompt(resolvedSystemPrompt, config.getStructuredOutput());
        }

        // 构建消息列表
        List<ChatMessage> messages = new ArrayList<>();

        // 添加系统消息
        if (resolvedSystemPrompt != null && !resolvedSystemPrompt.isEmpty()) {
            messages.add(SystemMessage.from(resolvedSystemPrompt));
        }

        // 处理 Memory 功能 - 加载历史对话
        if (config.isMemoryEnabled()) {
            List<ChatMessage> historyMessages = loadMemory(context.getExecutionId(), node.getId(), config.getMemoryWindowSize());
            messages.addAll(historyMessages);
        }

        // 构建用户消息（支持 Vision）
        if (resolvedUserPrompt != null && !resolvedUserPrompt.isEmpty()) {
            UserMessage userMessage = buildUserMessage(resolvedUserPrompt, config, context);
            messages.add(userMessage);
        }

        if (messages.isEmpty() || messages.stream().noneMatch(m -> m instanceof UserMessage)) {
            return NodeExecutionResult.failure("LLM 节点缺少用户提示词");
        }

        // 设置模型参数
        if (modelEntity.getVariables() == null) {
            modelEntity.setVariables(new HashMap<>());
        }
        modelEntity.getVariables().put("temperature", temperature);
        if (maxTokens != null) {
            modelEntity.getVariables().put("max_tokens", maxTokens);
        }

        // 获取模型并执行
        ChatModel chatModel = textModelService.model(modelEntity);
        ChatResponse response = chatModel.chat(messages);

        // 提取响应内容
        AiMessage aiMessage = response.aiMessage();
        String content = aiMessage.text();

        // 处理 Memory 功能 - 保存对话历史
        if (config.isMemoryEnabled()) {
            // 找到最后一个 UserMessage
            UserMessage lastUserMessage = null;
            for (int i = messages.size() - 1; i >= 0; i--) {
                if (messages.get(i) instanceof UserMessage) {
                    lastUserMessage = (UserMessage) messages.get(i);
                    break;
                }
            }
            if (lastUserMessage != null) {
                saveToMemory(context.getExecutionId(), node.getId(), lastUserMessage, aiMessage, config.getMemoryWindowSize());
            }
        }

        // 验证结构化输出
        Object parsedOutput = content;
        boolean structuredOutputValid = true;
        String structuredOutputError = null;

        if (config.getStructuredOutput() != null && config.getStructuredOutput().isEnabled()) {
            try {
                parsedOutput = validateAndParseStructuredOutput(content, config.getStructuredOutput());
            } catch (Exception e) {
                structuredOutputValid = false;
                structuredOutputError = e.getMessage();
                log.warn("节点 {} 的结构化输出校验失败: {}", node.getId(), e.getMessage());
            }
        }

        // 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", content);
        outputs.put("content", content);

        // 使用自定义输出变量名存储结果
        String outputVariable = config.getOutputVariable();
        if (outputVariable != null && !outputVariable.isEmpty()) {
            outputs.put(outputVariable, content);
        }

        // 添加结构化输出结果
        if (config.getStructuredOutput() != null && config.getStructuredOutput().isEnabled()) {
            outputs.put("parsedOutput", parsedOutput);
            outputs.put("structuredOutputValid", structuredOutputValid);
            if (structuredOutputError != null) {
                outputs.put("structuredOutputError", structuredOutputError);
            }
        }

        // 添加 token 使用信息
        if (response.tokenUsage() != null) {
            outputs.put("inputTokens", response.tokenUsage().inputTokenCount());
            outputs.put("outputTokens", response.tokenUsage().outputTokenCount());
            outputs.put("totalTokens", response.tokenUsage().totalTokenCount());
        }

        // 添加配置信息到输出
        outputs.put("visionEnabled", config.isVisionEnabled());
        outputs.put("memoryEnabled", config.isMemoryEnabled());

        log.debug("LLM 节点 {} 执行完成，响应长度: {}", node.getId(), content.length());
        return NodeExecutionResult.success(outputs);
    }

    /**
     * 构建用户消息（支持 Vision 图像输入）
     * @param config 节点配置
     * @param context 执行上下文
     * @param textPrompt textPrompt 参数
     * @return 处理结果
     */
    private UserMessage buildUserMessage(String textPrompt, LLMNodeConfig config, ExecutionContext context) {
        // 如果未启用 Vision 或没有图像变量，返回纯文本消息
        if (!config.isVisionEnabled() || config.getImageVariables() == null || config.getImageVariables().isEmpty()) {
            return UserMessage.from(textPrompt);
        }

        // 构建多模态消息内容
        List<dev.langchain4j.data.message.Content> contents = new ArrayList<>();

        // 添加文本内容
        contents.add(TextContent.from(textPrompt));

        // 添加图像内容
        for (String imageVar : config.getImageVariables()) {
            String resolvedImageRef = resolveTemplate(imageVar, context);
            if (resolvedImageRef != null && !resolvedImageRef.isEmpty()) {
                try {
                    ImageContent imageContent = createImageContent(resolvedImageRef);
                    if (imageContent != null) {
                        contents.add(imageContent);
                        log.debug("已从变量添加图像内容: {}", imageVar);
                    }
                } catch (Exception e) {
                    log.warn("处理图像变量 {} 失败: {}", imageVar, e.getMessage());
                }
            }
        }

        return UserMessage.from(contents);
    }

    /**
     * 创建图像内容
     * 支持 URL 和 Base64 格式
     * @param imageRef imageRef 参数
     * @return 处理结果
     */
    private ImageContent createImageContent(String imageRef) {
        if (imageRef == null || imageRef.isEmpty()) {
            return null;
        }

        // 检查是否是 URL
        if (imageRef.startsWith("http://") || imageRef.startsWith("https://")) {
            return ImageContent.from(Image.builder().url(URI.create(imageRef)).build());
        }

        // 检查是否是 Base64
        if (imageRef.startsWith("data:image/")) {
            // 格式: data:image/png;base64,xxxxx
            String[] parts = imageRef.split(",", 2);
            if (parts.length == 2) {
                String mimeType = parts[0].replace("data:", "").replace(";base64", "");
                String base64Data = parts[1];
                return ImageContent.from(Image.builder().base64Data(base64Data).mimeType(mimeType).build());
            }
        }

        // 尝试作为纯 Base64 处理
        return ImageContent.from(Image.builder().base64Data(imageRef).mimeType("image/png").build());
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

        // 返回最近的 N 轮对话（每轮包含 user + assistant）
        int maxMessages = (windowSize != null ? windowSize : 10) * 2;
        if (memory.size() <= maxMessages) {
            return new ArrayList<>(memory);
        }

        return new ArrayList<>(memory.subList(memory.size() - maxMessages, memory.size()));
    }

    /**
     * 保存对话到记忆
     * @param executionId 执行标识
     * @param aiMessage aiMessage 参数
     * @param nodeId 节点标识
     * @param userMessage userMessage 参数
     * @param windowSize windowSize 参数
     */
    private void saveToMemory(String executionId, String nodeId, UserMessage userMessage, AiMessage aiMessage, Integer windowSize) {
        Map<String, LinkedList<ChatMessage>> nodeMemories = MEMORY_STORE.computeIfAbsent(executionId, k -> new ConcurrentHashMap<>());
        LinkedList<ChatMessage> memory = nodeMemories.computeIfAbsent(nodeId, k -> new LinkedList<>());

        memory.add(userMessage);
        memory.add(aiMessage);

        // 限制记忆大小
        int maxMessages = (windowSize != null ? windowSize : 10) * 2;
        while (memory.size() > maxMessages) {
            memory.removeFirst();
        }
    }

    /**
     * 清理执行记忆（在执行完成后调用）
     * @param executionId 执行标识
     * @throws JsonProcessingException 处理失败时抛出
     */
    public static void clearMemory(String executionId) {
        MEMORY_STORE.remove(executionId);
    }

    /**
     * 验证并解析结构化输出
     * @param content 内容
     * @param structuredOutput structuredOutput 参数
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     * @throws JsonProcessingException 处理失败时抛出
     */
    private Object validateAndParseStructuredOutput(String content, LLMNodeConfig.StructuredOutput structuredOutput) throws JsonProcessingException {
        // 尝试提取 JSON 内容
        String jsonContent = extractJsonContent(content);

        // 解析 JSON
        JsonNode jsonNode = OBJECT_MAPPER.readTree(jsonContent);

        // 如果有 JSON Schema，进行验证
        if (structuredOutput.getJsonSchema() != null && !structuredOutput.getJsonSchema().isEmpty()) {
            // 基本的 JSON Schema 验证（检查必需字段）
            JsonNode schemaNode = OBJECT_MAPPER.readTree(structuredOutput.getJsonSchema());
            validateAgainstSchema(jsonNode, schemaNode);
        }

        return OBJECT_MAPPER.convertValue(jsonNode, Map.class);
    }

    /**
     * 从响应中提取 JSON 内容
     * @param content 内容
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private String extractJsonContent(String content) {
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("内容为空");
        }

        String trimmed = content.trim();

        // 如果已经是 JSON，直接返回
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            return trimmed;
        }

        // 尝试从 markdown 代码块中提取
        int jsonStart = trimmed.indexOf("```json");
        if (jsonStart >= 0) {
            int contentStart = trimmed.indexOf("\n", jsonStart) + 1;
            int contentEnd = trimmed.indexOf("```", contentStart);
            if (contentEnd > contentStart) {
                return trimmed.substring(contentStart, contentEnd).trim();
            }
        }

        // 尝试从普通代码块中提取
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

        // 尝试找到第一个 { 或 [ 并提取到对应的 } 或 ]
        int braceStart = trimmed.indexOf("{");
        int bracketStart = trimmed.indexOf("[");

        if (braceStart >= 0 && (bracketStart < 0 || braceStart < bracketStart)) {
            int braceEnd = findMatchingBrace(trimmed, braceStart, '{', '}');
            if (braceEnd > braceStart) {
                return trimmed.substring(braceStart, braceEnd + 1);
            }
        }

        if (bracketStart >= 0) {
            int bracketEnd = findMatchingBrace(trimmed, bracketStart, '[', ']');
            if (bracketEnd > bracketStart) {
                return trimmed.substring(bracketStart, bracketEnd + 1);
            }
        }

        throw new IllegalArgumentException("内容中未找到有效 JSON");
    }

    /**
     * 找到匹配的括号位置
     * @param close close 参数
     * @param content 内容
     * @param open open 参数
     * @param start start 参数
     * @return 处理结果
     */
    private int findMatchingBrace(String content, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        char prevChar = 0;

        for (int i = start; i < content.length(); i++) {
            char c = content.charAt(i);

            if (c == '"' && prevChar != '\\') {
                inString = !inString;
            } else if (!inString) {
                if (c == open) {
                    depth++;
                } else if (c == close) {
                    depth--;
                    if (depth == 0) {
                        return i;
                    }
                }
            }

            prevChar = c;
        }

        return -1;
    }

    /**
     * 基本的 JSON Schema 验证
     * @param data 业务数据
     * @param schema schema 参数
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private void validateAgainstSchema(JsonNode data, JsonNode schema) {
        // 检查 required 字段
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            for (JsonNode field : required) {
                String fieldName = field.asText();
                if (!data.has(fieldName) || data.get(fieldName).isNull()) {
                    throw new IllegalArgumentException("缺少必填字段: " + fieldName);
                }
            }
        }

        // 检查 properties 中定义的类型
        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            properties.fields().forEachRemaining(entry -> {
                String fieldName = entry.getKey();
                JsonNode fieldSchema = entry.getValue();
                JsonNode fieldValue = data.get(fieldName);

                if (fieldValue != null && !fieldValue.isNull()) {
                    JsonNode typeNode = fieldSchema.get("type");
                    if (typeNode != null) {
                        String expectedType = typeNode.asText();
                        validateFieldType(fieldName, fieldValue, expectedType);
                    }
                }
            });
        }
    }

    /**
     * 验证字段类型
     * @param expectedType expectedType 参数
     * @param fieldName fieldName 参数
     * @param value 参数值
     * @throws IllegalArgumentException 参数不合法时抛出
     */
    private void validateFieldType(String fieldName, JsonNode value, String expectedType) {
        boolean valid = switch (expectedType) {
            case "string" -> value.isTextual();
            case "number", "integer" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> true;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                    String.format("Field '%s' has invalid type. Expected %s but got %s",
                            fieldName, expectedType, value.getNodeType().toString().toLowerCase()));
        }
    }

    /**
     * 构建包含结构化输出指令的系统提示词
     * @param structuredOutput structuredOutput 参数
     * @param systemPrompt systemPrompt 参数
     * @return 处理结果
     */
    private String buildStructuredOutputPrompt(String systemPrompt, LLMNodeConfig.StructuredOutput structuredOutput) {
        StringBuilder sb = new StringBuilder();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            sb.append(systemPrompt).append("\n\n");
        }

        sb.append("## Output Format\n");
        if (structuredOutput.getDescription() != null) {
            sb.append(structuredOutput.getDescription()).append("\n\n");
        }

        if (structuredOutput.getJsonSchema() != null) {
            sb.append("Please respond with a valid JSON object that conforms to the following schema:\n");
            sb.append("```json\n").append(structuredOutput.getJsonSchema()).append("\n```\n");
        }

        if (structuredOutput.isStrictMode()) {
            sb.append("\nIMPORTANT: Your response MUST be a valid JSON object only, with no additional text.");
        }

        return sb.toString();
    }
}
