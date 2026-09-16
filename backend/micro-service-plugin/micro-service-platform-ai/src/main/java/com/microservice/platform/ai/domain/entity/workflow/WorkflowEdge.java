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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 工作流边定义
 * 连接两个节点的有向边
 *
 * @author xJh
 * @since 2026/01/06
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "WorkflowEdge", description = "工作流边")
public class WorkflowEdge implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "边ID")
    private String id;

    @Schema(description = "源节点ID")
    private String source;

    @Schema(description = "源端口ID")
    private String sourceHandle;

    @Schema(description = "目标节点ID")
    private String target;

    @Schema(description = "目标端口ID")
    private String targetHandle;
}
