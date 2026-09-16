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

package com.microservice.framework.ai.harness.tool;

import java.util.Objects;

/**
 * Tool 的不可变企业治理元数据。
 *
 * <p>名称、描述、参数 Schema 和执行器仍以 LangChain4j 为唯一事实源，本类型只表达企业治理规则。</p>
 *
 * @param toolName LangChain4j Tool 名称
 * @param toolsetId Tool 所属的稳定 Toolset 标识
 * @param domain 能力所属业务域
 * @param version Tool 契约版本
 * @param requiredPermission 所需权限码
 * @param risk 风险等级
 * @param write 是否产生业务写入
 * @param idempotent 是否具备业务幂等性
 * @param confirmationRequired 是否需要用户跨轮确认
 * @param approvalRequired 是否需要独立审批
 * @param verifier 权威回读 Verifier 名称
 * @param compensator 补偿器名称
 * @author xJh
 * @since 2026-07-18
 */
// 治理元数据是 Tool 装饰边界的不可变契约，保持扁平便于按名称和版本直接解析。
// CHECKSTYLE:OFF
public record ToolGovernance(String toolName, String toolsetId, String domain, String version, String requiredPermission,
        HarnessRiskLevel risk, boolean write, boolean idempotent, boolean confirmationRequired,
        boolean approvalRequired, String verifier, String compensator) {
    // CHECKSTYLE:ON

    public ToolGovernance {
        toolName = requireText(toolName, "工具名称");
        toolsetId = requireText(toolsetId, "Toolset 标识");
        domain = requireText(domain, "业务域");
        version = requireText(version, "治理版本");
        requiredPermission = requireText(requiredPermission, "工具权限");
        risk = Objects.requireNonNull(risk, "风险等级不能为空");
        verifier = trimToNull(verifier);
        compensator = trimToNull(compensator);
        if (write && !idempotent) {
            throw new IllegalArgumentException("写工具必须提供幂等语义");
        }
    }

    /** 返回持久化和审计使用的稳定身份。 */
    public String identity() {
        return domain + ":" + toolName + "@" + version;
    }

    private static String requireText(String value, String field) {
        String result = trimToNull(value);
        if (result == null) {
            throw new IllegalArgumentException(field + "不能为空");
        }
        return result;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
