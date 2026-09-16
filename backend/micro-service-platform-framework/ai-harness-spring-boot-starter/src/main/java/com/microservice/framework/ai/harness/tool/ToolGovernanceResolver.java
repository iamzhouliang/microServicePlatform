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

import dev.langchain4j.agent.tool.ToolSpecification;
import java.util.Optional;

/**
 * 根据官方 Tool Specification 解析企业治理元数据。
 *
 * @author xJh
 * @since 2026-07-18
 */
@FunctionalInterface
public interface ToolGovernanceResolver {
    
    /**
     * 未知工具必须返回空，受治理执行器将采用默认拒绝策略。
     *
     * @param specification LangChain4j 官方 Tool 定义
     * @return 对应的企业治理元数据
     */
    Optional<ToolGovernance> resolve(ToolSpecification specification);
}
