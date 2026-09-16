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

package com.microservice.framework.ai.harness.runtime;

import com.microservice.framework.ai.harness.runtime.model.ApprovalSummary;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import dev.langchain4j.agent.tool.ToolExecutionRequest;

/**
 * 将 Tool 参数投影为可供审批人阅读的脱敏业务摘要。
 *
 * <p>实现方不得返回密码、令牌、联系方式或原始参数正文。</p>
 */
@FunctionalInterface
public interface ApprovalSummaryProjector {
    
    ApprovalSummary project(ToolGovernance governance, ToolExecutionRequest request);
    
    static ApprovalSummaryProjector generic() {
        return (governance, request) -> new ApprovalSummary(
                "业务对象", "执行受治理的业务变更", "风险等级：" + governance.risk().name());
    }
}
