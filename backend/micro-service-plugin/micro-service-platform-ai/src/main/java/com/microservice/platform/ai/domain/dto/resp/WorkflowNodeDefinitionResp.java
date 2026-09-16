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

package com.microservice.platform.ai.domain.dto.resp;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodeConfigSchema;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodeDefinition;
import com.microservice.platform.ai.core.workflow.definition.WorkflowNodePortDefinition;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 工作流节点定义响应。
 *
 * @author xJh
 * @since 2026/05/25
 */
@Data
@Schema(description = "工作流节点定义响应")
public class WorkflowNodeDefinitionResp {

    @Schema(description = "节点类型")
    private NodeType type;

    @Schema(description = "展示名称")
    private String displayName;

    @Schema(description = "节点说明")
    private String description;

    @Schema(description = "节点分组")
    private String category;

    @Schema(description = "图标")
    private String icon;

    @Schema(description = "颜色")
    private String color;

    @Schema(description = "是否开始节点")
    private boolean start;

    @Schema(description = "是否终止节点")
    private boolean terminal;

    @Schema(description = "输入端口")
    private List<WorkflowNodePortDefinition> inputs;

    @Schema(description = "输出端口")
    private List<WorkflowNodePortDefinition> outputs;

    @Schema(description = "配置表单定义")
    private WorkflowNodeConfigSchema configSchema;

    @Schema(description = "默认配置")
    private Map<String, Object> defaultConfig;

    public static WorkflowNodeDefinitionResp of(WorkflowNodeDefinition definition) {
        WorkflowNodeDefinitionResp resp = new WorkflowNodeDefinitionResp();
        resp.setType(definition.type());
        resp.setDisplayName(definition.displayName());
        resp.setDescription(definition.description());
        resp.setCategory(definition.category());
        resp.setIcon(definition.icon());
        resp.setColor(definition.color());
        resp.setStart(definition.start());
        resp.setTerminal(definition.terminal());
        resp.setInputs(definition.inputs());
        resp.setOutputs(definition.outputs());
        resp.setConfigSchema(definition.configSchema());
        resp.setDefaultConfig(definition.defaultConfig());
        return resp;
    }
}
