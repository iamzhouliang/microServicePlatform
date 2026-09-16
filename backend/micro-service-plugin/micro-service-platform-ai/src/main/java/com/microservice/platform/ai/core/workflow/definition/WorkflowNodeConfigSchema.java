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

import java.util.List;

/**
 * 工作流节点配置表单定义。
 *
 * @param formComponent   前端表单组件名称
 * @param requiredFields  必填配置字段
 * @param outputVariables 默认输出变量名称
 * @param fields          字段级配置 Schema
 */
public record WorkflowNodeConfigSchema(
        String formComponent,
        List<String> requiredFields,
        List<String> outputVariables,
        List<WorkflowNodeConfigField> fields
) {
}
