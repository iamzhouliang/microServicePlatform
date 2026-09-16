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

import com.microservice.framework.ai.harness.capability.ToolsetContribution;
import com.microservice.framework.ai.harness.capability.ToolsetContributor;
import com.microservice.framework.ai.harness.capability.ToolsetSelectionContext;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

/**
 * 显式业务能力的只读工具目录。
 * <p>目录只消费 {@link ToolsetContributor}，并复用 LangChain4j 官方工具发现结果生成管理视图；
 * 不扫描 Spring 容器、不创建执行器，也不参与平台助手的工具执行。</p>
 *
 * @author xJh
 * @since 2026-07
 */
@Service
public class ToolService {

    private final List<ToolDTO> tools;

    public ToolService(List<ToolsetContributor> contributors) {
        Objects.requireNonNull(contributors, "Toolset 贡献者集合不能为空");
        ToolsetSelectionContext catalogContext = new ToolsetSelectionContext("catalog", "catalog", "catalog",
                "catalog", "BUSINESS", "CATALOG", "catalog", Set.of(), Set.of(), Set.of());
        this.tools = contributors.stream()
                .flatMap(contributor -> contributor.contribute(catalogContext).stream())
                .filter(contribution -> !contribution.descriptor().dynamic())
                .map(ToolService::toView)
                .toList();
    }

    /**
     * 返回管理页面使用的只读工具投影。
     * @return 处理结果
     */
    public List<ToolDTO> getTools() {
        return tools;
    }

    private static ToolDTO toView(ToolsetContribution contribution) {
        List<ToolMethodInfo> methods = contribution.tools().stream()
                .map(tool -> {
                    JsonObjectSchema schema = tool.toolSpecification().parameters();
                    List<ToolParameterInfo> parameters = schema == null ? List.of()
                            : schema.properties().entrySet().stream()
                                    .map(entry -> ToolParameterInfo.builder()
                                            .name(entry.getKey())
                                            .type(entry.getValue().getClass().getSimpleName())
                                            .description(entry.getValue().description())
                                            .build())
                                    .toList();
                    return ToolMethodInfo.builder()
                            .methodName(tool.name())
                            .name(tool.name())
                            .description(tool.toolSpecification().description())
                            .parameters(parameters)
                            .build();
                }).toList();
        return ToolDTO.builder()
                .toolsetId(contribution.descriptor().id())
                .name(contribution.descriptor().id())
                .description(contribution.descriptor().description())
                .source(contribution.descriptor().source().name())
                .dynamic(contribution.descriptor().dynamic())
                .methods(methods)
                .build();
    }

    @Data
    @Builder
    public static class ToolDTO {

        private String toolsetId;
        private String name;
        private String description;
        private String source;
        private boolean dynamic;
        private List<ToolMethodInfo> methods;
    }

    @Data
    @Builder
    public static class ToolMethodInfo {

        private String methodName;
        private String name;
        private String description;
        private List<ToolParameterInfo> parameters;
    }

    @Data
    @Builder
    public static class ToolParameterInfo {

        private String name;
        private String type;
        private String description;
    }
}
