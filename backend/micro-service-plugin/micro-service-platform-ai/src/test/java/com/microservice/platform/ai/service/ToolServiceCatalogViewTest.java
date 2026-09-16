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

package com.microservice.platform.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetDescriptor;
import com.microservice.framework.ai.harness.capability.ToolsetSource;
import com.microservice.framework.ai.harness.tool.HarnessRiskLevel;
import com.microservice.framework.ai.harness.tool.ToolGovernance;
import com.microservice.platform.ai.core.tools.PlatformToolService;
import com.microservice.platform.ai.core.tools.PlatformToolsetContributor;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ToolServiceCatalogViewTest {

    @Test
    void platformToolsetShouldExposeModelCompatibleToolNames() {
        PlatformToolService bean = new PlatformToolService();
        PlatformToolsetContributor contributor = new PlatformToolsetContributor(bean);
        var contribution = contributor.contribute(null).iterator().next();
        List<String> names = dev.langchain4j.service.tool.ToolService.findTools(bean).stream()
                .map(dev.langchain4j.service.tool.AiServiceTool::name)
                .toList();

        assertThat(names).allMatch(name -> name.matches("^[a-zA-Z0-9_-]+$"));
        assertThat(contribution.governanceByName().keySet()).containsExactlyInAnyOrderElementsOf(names);
        assertThat(contribution.descriptor().id()).isEqualTo(PlatformToolsetContributor.TOOLSET_ID);
    }

    @Test
    void shouldProjectExplicitToolsetWithOfficialToolMetadata() {
        NumberTool bean = new NumberTool();
        ToolsetDescriptor descriptor = new ToolsetDescriptor("module:test:number", "1.0.0", ToolsetSource.MODULE,
                "数字处理", Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), false);
        ToolsetContribution contribution = ToolsetContribution.fromBeans(descriptor, List.of(bean), List.of(
                new ToolGovernance("number_increment", "module:test:number", "number", "1.0.0", "*",
                        HarnessRiskLevel.LOW,
                        false, true, false, false, null, null)));
        ToolService service = new ToolService(List.of(context -> List.of(contribution)));

        assertThat(service.getTools()).singleElement().satisfies(tool -> {
            assertThat(tool.getToolsetId()).isEqualTo("module:test:number");
            assertThat(tool.getDescription()).isEqualTo("数字处理");
            assertThat(tool.getMethods()).singleElement().satisfies(method -> {
                assertThat(method.getName()).isEqualTo("number_increment");
                assertThat(method.getDescription()).isEqualTo("将整数加一");
            });
        });
    }

    static class NumberTool {

        @Tool(name = "number_increment", value = "将整数加一")
        public int increment(@P("待处理整数") int number) {
            return number + 1;
        }
    }
}
