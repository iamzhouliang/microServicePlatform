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

package com.microservice.platform.ai.core.workflow.definition;

import com.microservice.platform.ai.core.enums.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowNodeDefinitionCatalogTest {

    @Test
    @DisplayName("节点定义中心覆盖全部后端节点类型")
    void shouldCoverAllNodeTypes() {
        List<WorkflowNodeDefinition> definitions = WorkflowNodeDefinitionCatalog.all();
        Set<NodeType> definedTypes = definitions.stream()
                .map(WorkflowNodeDefinition::type)
                .collect(Collectors.toSet());

        assertThat(definedTypes).containsExactlyInAnyOrder(NodeType.values());
    }

    @Test
    @DisplayName("节点定义中心输出产品化名称和统一端口")
    void shouldExposeProductNamesAndPorts() {
        WorkflowNodeDefinition start = WorkflowNodeDefinitionCatalog.require(NodeType.START);
        WorkflowNodeDefinition end = WorkflowNodeDefinitionCatalog.require(NodeType.END);
        WorkflowNodeDefinition ifElse = WorkflowNodeDefinitionCatalog.require(NodeType.IF_ELSE);
        WorkflowNodeDefinition loop = WorkflowNodeDefinitionCatalog.require(NodeType.LOOP);

        assertThat(start.displayName()).isEqualTo("开始");
        assertThat(end.displayName()).isEqualTo("结束");
        assertThat(start.outputs()).extracting(WorkflowNodePortDefinition::id).containsExactly("output");
        assertThat(end.inputs()).extracting(WorkflowNodePortDefinition::id).containsExactly("input");
        assertThat(ifElse.outputs()).allMatch(port -> port.id().startsWith("branch:"));
        assertThat(loop.outputs()).extracting(WorkflowNodePortDefinition::id)
                .containsExactly("branch:loop", "branch:exit");
    }

    @Test
    @DisplayName("节点定义字段名与运行配置类保持一致")
    void shouldExposeRuntimeConfigFieldNames() {
        WorkflowNodeDefinition iteration = WorkflowNodeDefinitionCatalog.require(NodeType.ITERATION);
        WorkflowNodeDefinition listOperator = WorkflowNodeDefinitionCatalog.require(NodeType.LIST_OPERATOR);
        WorkflowNodeDefinition parallel = WorkflowNodeDefinitionCatalog.require(NodeType.PARALLEL);

        assertThat(fieldNames(iteration))
                .contains("arrayVariable", "processingMode", "outputVariable")
                .doesNotContain("itemsVariable");
        assertThat(iteration.defaultConfig())
                .containsEntry("arrayVariable", "")
                .containsEntry("processingMode", "SEQUENTIAL")
                .containsEntry("outputVariable", "results");

        assertThat(fieldNames(listOperator))
                .contains("operationType", "inputVariable", "outputVariable")
                .doesNotContain("operation");
        assertThat(listOperator.defaultConfig())
                .containsEntry("operationType", "FILTER")
                .containsEntry("outputVariable", "list_result");

        assertThat(fieldNames(parallel)).contains("waitStrategy");
        assertThat(parallel.defaultConfig()).containsEntry("waitStrategy", "ALL");
    }

    private static List<String> fieldNames(WorkflowNodeDefinition definition) {
        return definition.configSchema().fields().stream()
                .map(WorkflowNodeConfigField::name)
                .toList();
    }
}
