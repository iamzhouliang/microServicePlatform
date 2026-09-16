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

import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import dev.langchain4j.service.tool.AiServiceTool;
import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderRequest;
import dev.langchain4j.service.tool.ToolProviderResult;
import java.util.Objects;

/**
 * 对任意官方 ToolProvider 结果执行统一治理装饰。
 *
 * <p>静态 Java Tool、Skills 和 MCP 都通过该边界进入 AI Service，Harness 不复制 Schema 或执行协议。</p>
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class GovernedToolProvider implements ToolProvider {
    
    private final ToolProvider delegate;
    private final ToolGovernanceResolver governanceResolver;
    private final HarnessOperationCoordinator operationCoordinator;
    
    public GovernedToolProvider(ToolProvider delegate, ToolGovernanceResolver governanceResolver) {
        this(delegate, governanceResolver, null);
    }
    
    public GovernedToolProvider(ToolProvider delegate, ToolGovernanceResolver governanceResolver,
                                HarnessOperationCoordinator operationCoordinator) {
        this.delegate = Objects.requireNonNull(delegate, "ToolProvider 委托不能为空");
        this.governanceResolver = Objects.requireNonNull(governanceResolver, "治理解析器不能为空");
        this.operationCoordinator = operationCoordinator;
    }
    
    @Override
    public ToolProviderResult provideTools(ToolProviderRequest request) {
        ToolProviderResult original = delegate.provideTools(request);
        ToolProviderResult.Builder result = ToolProviderResult.builder();
        original.aiServiceTools().forEach(tool -> result.add(decorate(tool)));
        return result.build();
    }
    
    @Override
    public boolean isDynamic() {
        return delegate.isDynamic();
    }
    
    private AiServiceTool decorate(AiServiceTool tool) {
        // Skill 内部的请求级 Toolset Provider 已完成治理绑定，外层只负责系统控制工具。
        if (tool.toolExecutor() instanceof GovernedToolExecutor) {
            return tool;
        }
        return tool.toBuilder()
                .toolExecutor(governanceResolver
                        .resolve(tool.toolSpecification()).<dev.langchain4j.service.tool.ToolExecutor>map(rule -> new GovernedToolExecutor(rule, tool.toolExecutor(), operationCoordinator))
                        .orElseGet(() -> GovernedToolExecutor.unregistered(tool.toolExecutor())))
                .build();
    }
}
