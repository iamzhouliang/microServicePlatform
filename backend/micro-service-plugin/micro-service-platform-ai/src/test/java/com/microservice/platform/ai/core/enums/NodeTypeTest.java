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

package com.microservice.platform.ai.core.enums;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * NodeType 枚举单元测试
 *
 * @author xJh
 * @since 2026/01/07
 */
@DisplayName("NodeType 枚举测试")
class NodeTypeTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Nested
    @DisplayName("枚举值完整性测试")
    class EnumCompletenessTests {

        @Test
        @DisplayName("应包含所有基础节点类型")
        void shouldContainAllBasicNodeTypes() {
            Set<String> basicTypes = Set.of("START", "END", "VARIABLE_ASSIGNER");

            for (String type : basicTypes) {
                assertDoesNotThrow(() -> NodeType.valueOf(type),
                        "应包含基础节点类型: " + type);
            }
        }

        @Test
        @DisplayName("应包含所有 AI 节点类型")
        void shouldContainAllAINodeTypes() {
            Set<String> aiTypes = Set.of(
                    "LLM", "KNOWLEDGE_RETRIEVAL", "QUESTION_CLASSIFIER",
                    "PARAMETER_EXTRACTOR", "AGENT");

            for (String type : aiTypes) {
                assertDoesNotThrow(() -> NodeType.valueOf(type),
                        "应包含 AI 节点类型: " + type);
            }
        }

        @Test
        @DisplayName("应包含所有逻辑节点类型")
        void shouldContainAllLogicNodeTypes() {
            Set<String> logicTypes = Set.of(
                    "IF_ELSE", "ITERATION", "VARIABLE_AGGREGATOR", "LOOP", "PARALLEL");

            for (String type : logicTypes) {
                assertDoesNotThrow(() -> NodeType.valueOf(type),
                        "应包含逻辑节点类型: " + type);
            }
        }

        @Test
        @DisplayName("应包含所有数据处理节点类型")
        void shouldContainAllDataProcessingNodeTypes() {
            Set<String> dataTypes = Set.of(
                    "CODE", "TEMPLATE", "DOC_EXTRACTOR", "LIST_OPERATOR");

            for (String type : dataTypes) {
                assertDoesNotThrow(() -> NodeType.valueOf(type),
                        "应包含数据处理节点类型: " + type);
            }
        }

        @Test
        @DisplayName("应包含所有集成节点类型")
        void shouldContainAllIntegrationNodeTypes() {
            Set<String> integrationTypes = Set.of("HTTP_REQUEST", "TOOL");

            for (String type : integrationTypes) {
                assertDoesNotThrow(() -> NodeType.valueOf(type),
                        "应包含集成节点类型: " + type);
            }
        }

        @Test
        @DisplayName("不应包含废弃的节点类型")
        void shouldNotContainDeprecatedNodeTypes() {
            Set<String> deprecatedTypes = Set.of("CONDITION", "KNOWLEDGE", "VARIABLE", "HTTP");

            for (String type : deprecatedTypes) {
                assertThrows(IllegalArgumentException.class, () -> NodeType.valueOf(type),
                        "不应包含废弃的节点类型: " + type);
            }
        }

        @Test
        @DisplayName("枚举值数量应正确")
        void shouldHaveCorrectEnumCount() {
            // 基础节点: 3, AI节点: 5, 逻辑节点: 5, 数据处理节点: 4, 集成节点: 2
            int expectedCount = 3 + 5 + 5 + 4 + 2;
            assertEquals(expectedCount, NodeType.values().length,
                    "NodeType 枚举值数量应为 " + expectedCount);
        }
    }

    @Nested
    @DisplayName("枚举属性测试")
    class EnumPropertyTests {

        @Test
        @DisplayName("每个枚举值应有非空的 code")
        void eachEnumShouldHaveNonEmptyCode() {
            for (NodeType type : NodeType.values()) {
                assertNotNull(type.getCode(), type.name() + " 的 code 不应为 null");
                assertFalse(type.getCode().isEmpty(), type.name() + " 的 code 不应为空");
            }
        }

        @Test
        @DisplayName("每个枚举值应有非空的 description")
        void eachEnumShouldHaveNonEmptyDescription() {
            for (NodeType type : NodeType.values()) {
                assertNotNull(type.getDescription(), type.name() + " 的 description 不应为 null");
                assertFalse(type.getDescription().isEmpty(), type.name() + " 的 description 不应为空");
            }
        }

        @Test
        @DisplayName("每个枚举值应有非空的 detail")
        void eachEnumShouldHaveNonEmptyDetail() {
            for (NodeType type : NodeType.values()) {
                assertNotNull(type.getDetail(), type.name() + " 的 detail 不应为 null");
                assertFalse(type.getDetail().isEmpty(), type.name() + " 的 detail 不应为空");
            }
        }

        @Test
        @DisplayName("code 应与枚举名称一致")
        void codeShouldMatchEnumName() {
            for (NodeType type : NodeType.values()) {
                assertEquals(type.name(), type.getCode(),
                        type.name() + " 的 code 应与枚举名称一致");
            }
        }

        @Test
        @DisplayName("所有 code 应唯一")
        void allCodesShouldBeUnique() {
            Set<String> codes = Arrays.stream(NodeType.values())
                    .map(NodeType::getCode)
                    .collect(Collectors.toSet());

            assertEquals(NodeType.values().length, codes.size(),
                    "所有 NodeType 的 code 应唯一");
        }
    }

    @Nested
    @DisplayName("序列化/反序列化测试")
    class SerializationTests {

        @Test
        @DisplayName("应能正确序列化为 JSON")
        void shouldSerializeToJson() throws JsonProcessingException {
            for (NodeType type : NodeType.values()) {
                String json = OBJECT_MAPPER.writeValueAsString(type);
                assertNotNull(json);
                assertTrue(json.contains(type.name()),
                        type.name() + " 序列化后应包含枚举名称");
            }
        }

        @Test
        @DisplayName("应能正确从 JSON 反序列化")
        void shouldDeserializeFromJson() throws JsonProcessingException {
            for (NodeType type : NodeType.values()) {
                String json = "\"" + type.name() + "\"";
                NodeType deserialized = OBJECT_MAPPER.readValue(json, NodeType.class);
                assertEquals(type, deserialized,
                        type.name() + " 应能正确反序列化");
            }
        }

        @Test
        @DisplayName("序列化后反序列化应得到相同值")
        void serializeDeserializeShouldBeEqual() throws JsonProcessingException {
            for (NodeType type : NodeType.values()) {
                String json = OBJECT_MAPPER.writeValueAsString(type);
                NodeType deserialized = OBJECT_MAPPER.readValue(json, NodeType.class);
                assertEquals(type, deserialized,
                        type.name() + " 序列化后反序列化应得到相同值");
            }
        }
    }

    @Nested
    @DisplayName("节点分类测试")
    class NodeCategoryTests {

        @Test
        @DisplayName("START 节点应为基础节点")
        void startShouldBeBasicNode() {
            NodeType type = NodeType.START;
            assertEquals("用户输入", type.getDescription());
        }

        @Test
        @DisplayName("LLM 节点应为 AI 节点")
        void llmShouldBeAINode() {
            NodeType type = NodeType.LLM;
            assertEquals("大模型", type.getDescription());
        }

        @Test
        @DisplayName("IF_ELSE 节点应为逻辑节点")
        void ifElseShouldBeLogicNode() {
            NodeType type = NodeType.IF_ELSE;
            assertEquals("条件分支", type.getDescription());
        }

        @Test
        @DisplayName("CODE 节点应为数据处理节点")
        void codeShouldBeDataProcessingNode() {
            NodeType type = NodeType.CODE;
            assertEquals("代码", type.getDescription());
        }

        @Test
        @DisplayName("HTTP_REQUEST 节点应为集成节点")
        void httpRequestShouldBeIntegrationNode() {
            NodeType type = NodeType.HTTP_REQUEST;
            assertEquals("HTTP请求", type.getDescription());
        }
    }
}
