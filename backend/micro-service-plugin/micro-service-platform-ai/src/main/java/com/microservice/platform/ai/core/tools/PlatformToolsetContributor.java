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

package com.microservice.platform.ai.core.tools;

import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetContributor;
import com.microservice.framework.ai.harness.capability.ToolsetDescriptor;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

/** 平台基础只读能力 Toolset。 */
@Component
public final class PlatformToolsetContributor implements ToolsetContributor {

    public static final String TOOLSET_ID = "module:platform:information";

    private final ToolsetContribution contribution;

    public PlatformToolsetContributor(PlatformToolService toolService) {
        ToolsetDescriptor descriptor = new ToolsetDescriptor(TOOLSET_ID, "1.0.0", ToolsetSource.MODULE,
                "平台菜单、功能和文本分析", Set.of("BUSINESS"), Set.of(), Set.of(), Set.of(), Set.of(), false);
        this.contribution = ToolsetContribution.fromBeans(descriptor, List.of(toolService),
                List.of(read(PlatformToolService.QUERY_MENU), read(PlatformToolService.ANALYZE_TEXT),
                        read(PlatformToolService.LIST_FEATURES)));
    }

    @Override
    public Collection<ToolsetContribution> contribute(ToolsetSelectionContext context) {
        return List.of(contribution);
    }

    private static ToolGovernance read(String name) {
        return new ToolGovernance(name, TOOLSET_ID, "platform", "1.0.0", "*", HarnessRiskLevel.LOW,
                false, true, false, false, null, null);
    }
}
