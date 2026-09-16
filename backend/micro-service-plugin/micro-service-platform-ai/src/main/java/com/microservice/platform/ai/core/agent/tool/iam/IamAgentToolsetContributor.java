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

package com.microservice.platform.ai.core.agent.tool.iam;

import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetContributor;
import com.microservice.framework.ai.harness.capability.ToolsetDescriptor;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** IAM 用户管理业务向 Harness 贡献的原生 LangChain4j Toolset。 */
@Component
public final class IamAgentToolsetContributor implements ToolsetContributor {

    public static final String TOOLSET_ID = "module:iam:user-management";

    private final ToolsetContribution contribution;

    public IamAgentToolsetContributor(IamAgentToolService toolService) {
        Objects.requireNonNull(toolService, "IAM 工具服务不能为空");
        ToolsetDescriptor descriptor = new ToolsetDescriptor(TOOLSET_ID, "1.0.0", ToolsetSource.MODULE,
                "IAM 用户查询、创建、角色分配与结果核验", Set.of("BUSINESS"), Set.of(), Set.of(), Set.of(),
                Set.of(), false);
        this.contribution = ToolsetContribution.fromBeans(descriptor, List.of(toolService), List.of(
                read(IamAgentTools.RESOLVE_ORG, "sys:user:add"),
                read(IamAgentTools.RESOLVE_ROLE, "sys:role:assign-users"),
                read(IamAgentTools.SEARCH_USER, "sys:user:page"),
                read(IamAgentTools.VERIFY_USER, "sys:user:page"),
                new ToolGovernance(IamAgentTools.PROVISION_USER, TOOLSET_ID, "iam", "1.0.0",
                        "sys:user:add,sys:role:assign-users", HarnessRiskLevel.HIGH, true, true,
                        true, true, IamAgentTools.VERIFY_USER, null)));
    }

    @Override
    public Collection<ToolsetContribution> contribute(ToolsetSelectionContext context) {
        return List.of(contribution);
    }

    private static ToolGovernance read(String name, String permission) {
        return new ToolGovernance(name, TOOLSET_ID, "iam", "1.0.0", permission, HarnessRiskLevel.LOW,
                false, true, false, false, null, null);
    }
}
