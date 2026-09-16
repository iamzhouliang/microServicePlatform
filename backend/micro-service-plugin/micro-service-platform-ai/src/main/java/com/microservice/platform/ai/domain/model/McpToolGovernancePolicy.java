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

package com.microservice.platform.ai.domain.model;

import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import java.util.Objects;

/**
 * MCP 单个 Tool 的显式治理策略。
 *
 * @param requiredPermission 执行 Tool 所需权限
 * @param risk 风险等级
 * @param write 是否为写操作
 * @param idempotent 是否由提供方保证幂等
 * @param confirmationRequired 是否需要跨用户轮确认
 * @param approvalRequired 是否需要独立审批
 * @param verifier 结果未知时使用的验证器标识
 * @param compensator 补偿器标识
 * @author xJh
 * @since 2026-07-20
 */
public record McpToolGovernancePolicy(String requiredPermission, HarnessRiskLevel risk, boolean write,
        boolean idempotent, boolean confirmationRequired, boolean approvalRequired, String verifier,
        String compensator) {

    public McpToolGovernancePolicy {
        if (requiredPermission == null || requiredPermission.isBlank()) {
            throw new IllegalArgumentException("MCP Tool 权限不能为空");
        }
        requiredPermission = requiredPermission.trim();
        risk = Objects.requireNonNull(risk, "MCP Tool 风险等级不能为空");
        if (write && !idempotent) {
            throw new IllegalArgumentException("MCP 写 Tool 必须由提供方声明并实现幂等语义");
        }
        verifier = trimToNull(verifier);
        compensator = trimToNull(compensator);
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
