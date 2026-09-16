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

package com.microservice.platform.ai.domain.entity.workflow;

import com.microservice.platform.ai.core.enums.NodeType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

/**
 * 工作流节点定义
 *
 * @author xJh
 * @since 2026/01/06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WorkflowNode", description = "工作流节点")
public class WorkflowNode implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "节点ID")
    private String id;

    @Schema(description = "节点类型: START, END, VARIABLE_ASSIGNER, LLM, KNOWLEDGE_RETRIEVAL, "
            + "QUESTION_CLASSIFIER, PARAMETER_EXTRACTOR, AGENT, IF_ELSE, ITERATION, VARIABLE_AGGREGATOR, "
            + "LOOP, PARALLEL, CODE, TEMPLATE, DOC_EXTRACTOR, LIST_OPERATOR, HTTP_REQUEST, TOOL")
    private NodeType type;

    @Schema(description = "节点标签/名称")
    private String label;

    @Schema(description = "节点位置")
    private Position position;

    @Schema(description = "节点配置数据(使用Map灵活存储不同类型节点的配置)")
    private Map<String, Object> data;

    /**
     * 节点位置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(name = "Position", description = "节点位置")
    public static class Position implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "X坐标")
        private Double x;

        @Schema(description = "Y坐标")
        private Double y;
    }
}
