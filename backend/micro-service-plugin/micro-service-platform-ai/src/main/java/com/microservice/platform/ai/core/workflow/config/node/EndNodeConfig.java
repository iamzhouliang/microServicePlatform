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

package com.microservice.platform.ai.core.workflow.config.node;

import com.microservice.platform.ai.core.workflow.enums.WorkflowValueType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * END 结束节点配置
 * 简化设计：只通过 outputs 列表定义输出变量
 * 每个输出变量可以是变量引用（如 {{nodeId.varName}}）或固定值
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EndNodeConfig {

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 输出变量列表
     */
    private List<OutputField> outputs;

    /**
     * 获取所有依赖的节点ID
     *
     * @return 节点ID集合
     */
    public Set<String> getDependentNodeIds() {
        Set<String> nodeIds = new HashSet<>();
        if (outputs == null) {
            return nodeIds;
        }
        for (OutputField output : outputs) {
            String value = output.getValue();
            if (value != null && value.startsWith("{{") && value.endsWith("}}")) {
                Matcher matcher = VARIABLE_PATTERN.matcher(value);
                if (matcher.find()) {
                    String varPath = matcher.group(1).trim();
                    String[] parts = varPath.split("\\.", 2);
                    nodeIds.add(parts[0]);
                }
            }
        }
        return nodeIds;
    }

    /**
     * 输出字段定义
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class OutputField {

        /**
         * 输出变量名
         */
        private String name;

        /**
         * 变量类型
         */
        private WorkflowValueType type;

        /**
         * 变量值（固定值或变量引用）
         */
        private String value;

        /**
         * 字段描述
         */
        private String description;

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            OutputField that = (OutputField) o;
            return Objects.equals(name, that.name)
                    && type == that.type
                    && Objects.equals(value, that.value)
                    && Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, type, value, description);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EndNodeConfig that = (EndNodeConfig) o;
        return Objects.equals(outputs, that.outputs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(outputs);
    }
}
