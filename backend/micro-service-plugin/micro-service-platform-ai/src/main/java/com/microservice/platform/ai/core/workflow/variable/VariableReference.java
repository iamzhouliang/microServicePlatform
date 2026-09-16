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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 变量引用
 * 表示一个变量引用的解析结果
 *
 * @author xJh
 * @since 2026/01/08
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VariableReference {

    /**
     * 完整表达式 (e.g., "nodeName.variableName")
     */
    private String fullExpression;

    /**
     * 节点 ID
     */
    private String nodeId;

    /**
     * 变量路径 (不包含节点 ID)
     */
    private String variablePath;

    /**
     * 获取完整的变量路径 (包含节点 ID)
     * @return 处理结果
     */
    public String getFullPath() {
        if (variablePath == null || variablePath.isEmpty()) {
            return nodeId;
        }
        return nodeId + "." + variablePath;
    }

    /**
     * 转换为变量引用字符串
     * @return 处理结果
     */
    public String toReferenceString() {
        StringBuilder sb = new StringBuilder("{{");
        sb.append(getFullPath());
        sb.append("}}");
        return sb.toString();
    }
}
