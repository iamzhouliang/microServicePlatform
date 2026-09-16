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

package com.microservice.platform.ai.core.workflow.variable;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 变量解析器
 * 解析 {{nodeName.variableName}} 格式的变量引用
 * 支持嵌套路径访问 {{node.output.data[0].id}}
 *
 * @author xJh
 * @since 2026/01/08
 */
@Slf4j
@Component
public class VariableResolver {

    /**
     * 变量引用模式：{{nodeName.variableName}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)}}");

    /**
     * 数组索引模式：[0], [1], etc.
     */
    private static final Pattern ARRAY_INDEX_PATTERN = Pattern.compile("\\[(\\d+)]");

    /**
     * JSON 序列化器
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 解析字符串模板中的所有变量引用
     *
     * @param template    模板字符串
     * @param nodeOutputs 节点输出映射 (nodeLabel -> outputs)
     * @return 解析后的字符串
     */
    public String resolve(String template, Map<String, Object> nodeOutputs) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            Object value = resolveExpression(expression, nodeOutputs);
            String replacement = formatValue(value);
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * 解析单个变量表达式
     *
     * @param expression  变量表达式 (e.g., "nodeName.variableName")
     * @param nodeOutputs 节点输出映射
     * @return 解析后的值
     */
    public Object resolveExpression(String expression, Map<String, Object> nodeOutputs) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }

        // 解析变量路径
        return resolveVariablePath(expression, nodeOutputs);
    }

    /**
     * 解析变量路径
     * 支持格式:
     * - nodeLabel.variableName
     * - nodeLabel.variableName.nested.path
     * - nodeLabel.variableName[0].property
     * - nodeLabel.variableName.data[0].items[1].id
     *
     * @param path        变量路径
     * @param nodeOutputs 节点输出映射 (nodeLabel -> outputs)
     * @return 解析后的值
     */
    public Object resolveVariablePath(String path, Map<String, Object> nodeOutputs) {
        if (path == null || path.isEmpty() || nodeOutputs == null) {
            return null;
        }

        // 分割路径，第一部分是节点标签
        String[] parts = splitPath(path);
        if (parts.length == 0) {
            return null;
        }

        String nodeLabel = parts[0];
        Object nodeOutput = nodeOutputs.get(nodeLabel);

        if (nodeOutput == null) {
            log.debug("未找到节点标签对应的输出: {}", nodeLabel);
            return null;
        }

        // 如果只有节点标签，返回整个节点输出
        if (parts.length == 1) {
            return nodeOutput;
        }

        // 遍历剩余路径
        Object current = nodeOutput;
        for (int i = 1; i < parts.length && current != null; i++) {
            current = resolvePathSegment(current, parts[i]);
        }

        return current;
    }

    /**
     * 解析路径中的单个段
     * @param current current 参数
     * @param segment segment 参数
     * @return 处理结果
     */
    private Object resolvePathSegment(Object current, String segment) {
        if (current == null || segment == null || segment.isEmpty()) {
            return null;
        }

        // 检查是否包含数组索引
        Matcher arrayMatcher = ARRAY_INDEX_PATTERN.matcher(segment);
        if (arrayMatcher.find()) {
            // 分离属性名和数组索引
            String propertyName = segment.substring(0, arrayMatcher.start());

            // 先获取属性值
            Object propertyValue = current;
            if (!propertyName.isEmpty()) {
                propertyValue = getPropertyValue(current, propertyName);
            }

            // 处理所有数组索引（支持多层如 [1][0]）
            arrayMatcher.reset();
            while (arrayMatcher.find() && propertyValue != null) {
                int index = Integer.parseInt(arrayMatcher.group(1));
                propertyValue = getArrayElement(propertyValue, index);
            }

            return propertyValue;
        }

        // 普通属性访问
        return getPropertyValue(current, segment);
    }

    /**
     * 获取对象的属性值
     * @param obj obj 参数
     * @param propertyName propertyName 参数
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    private Object getPropertyValue(Object obj, String propertyName) {
        if (obj == null || propertyName == null || propertyName.isEmpty()) {
            return null;
        }

        // Map 类型
        if (obj instanceof Map) {
            return ((Map<String, Object>) obj).get(propertyName);
        }

        // JsonNode 类型
        if (obj instanceof JsonNode jsonNode) {
            JsonNode child = jsonNode.get(propertyName);
            return convertJsonNode(child);
        }

        // 尝试反射获取属性（用于 POJO）
        try {
            java.lang.reflect.Field field = obj.getClass().getDeclaredField(propertyName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            // 尝试 getter 方法
            try {
                String getterName = "get" + Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
                java.lang.reflect.Method getter = obj.getClass().getMethod(getterName);
                return getter.invoke(obj);
            } catch (Exception ex) {
                log.debug("从对象读取属性 '{}' 失败: {}", propertyName, ex.getMessage());
                return null;
            }
        }
    }

    /**
     * 获取数组/列表元素
     * @param index 索引位置
     * @param obj obj 参数
     * @return 处理结果
     */
    @SuppressWarnings("unchecked")
    private Object getArrayElement(Object obj, int index) {
        if (obj == null || index < 0) {
            return null;
        }

        // List 类型
        if (obj instanceof List<?> list) {
            if (index < list.size()) {
                return list.get(index);
            }
            return null;
        }

        // 数组类型
        if (obj.getClass().isArray()) {
            Object[] array = (Object[]) obj;
            if (index < array.length) {
                return array[index];
            }
            return null;
        }

        // JsonNode 数组
        if (obj instanceof JsonNode jsonNode && jsonNode.isArray()) {
            if (index < jsonNode.size()) {
                return convertJsonNode(jsonNode.get(index));
            }
            return null;
        }

        return null;
    }

    /**
     * 转换 JsonNode 为 Java 对象
     * @param node 工作流节点
     * @return 处理结果
     */
    private Object convertJsonNode(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isNumber()) {
            if (node.isInt()) {
                return node.asInt();
            }
            if (node.isLong()) {
                return node.asLong();
            }
            return node.asDouble();
        }
        if (node.isBoolean()) {
            return node.asBoolean();
        }
        if (node.isArray()) {
            List<Object> list = new ArrayList<>();
            for (JsonNode element : node) {
                list.add(convertJsonNode(element));
            }
            return list;
        }
        if (node.isObject()) {
            return OBJECT_MAPPER.convertValue(node, Map.class);
        }
        return node;
    }

    /**
     * 分割路径字符串
     * 处理点号分隔和数组索引
     * e.g., "node.data[0].items[1].id" -> ["node", "data[0]", "items[1]", "id"]
     * @param path 资源路径
     * @return 处理结果
     */
    private String[] splitPath(String path) {
        if (path == null || path.isEmpty()) {
            return new String[0];
        }

        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inBracket = false;

        for (char c : path.toCharArray()) {
            if (c == '[') {
                inBracket = true;
                current.append(c);
            } else if (c == ']') {
                inBracket = false;
                current.append(c);
            } else if (c == '.' && !inBracket) {
                if (!current.isEmpty()) {
                    parts.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (!current.isEmpty()) {
            parts.add(current.toString());
        }

        return parts.toArray(new String[0]);
    }

    /**
     * 格式化值为字符串
     * @param value 参数值
     * @return 处理结果
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Map || value instanceof List) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                log.warn("序列化变量值为 JSON 失败: {}", e.getMessage());
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    /**
     * 检查字符串是否包含变量引用
     *
     * @param text 要检查的字符串
     * @return 是否包含变量引用
     */
    public boolean containsVariableReference(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return VARIABLE_PATTERN.matcher(text).find();
    }

    /**
     * 提取字符串中的所有变量引用
     *
     * @param text 要检查的字符串
     * @return 变量引用列表
     */
    public List<VariableReference> extractVariableReferences(String text) {
        List<VariableReference> references = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return references;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(text);
        while (matcher.find()) {
            String expression = matcher.group(1).trim();
            references.add(parseVariableReference(expression));
        }

        return references;
    }

    /**
     * 解析变量引用
     * @param expression expression 参数
     * @return 处理结果
     */
    private VariableReference parseVariableReference(String expression) {
        String[] parts = splitPath(expression);
        String nodeId = parts.length > 0 ? parts[0] : null;
        String variablePath = parts.length > 1 ? expression.substring(nodeId.length() + 1) : null;

        return VariableReference.builder()
                .fullExpression(expression)
                .nodeId(nodeId)
                .variablePath(variablePath)
                .build();
    }
}
