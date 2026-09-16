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

package com.microservice.framework.ai.harness.dependency;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * LangChain4j 原生能力依赖契约。
 *
 * <p>Harness 只装饰官方 Tool/Skills 执行边界，不允许重新实现这些基础能力。</p>
 */
class LangChain4jDependencyContractTest {
    
    @Test
    void shouldExposeOfficialSkillsAndToolRuntimeContracts() throws Exception {
        Class<?> skills = Class.forName("dev.langchain4j.skills.Skills");
        Class<?> toolService = Class.forName("dev.langchain4j.service.tool.ToolService");
        Class<?> toolProvider = Class.forName("dev.langchain4j.service.tool.ToolProvider");
        Class<?> invocationParameters = Class.forName("dev.langchain4j.invocation.InvocationParameters");
        
        assertThat(skills).isNotNull();
        assertThat(methodNames(toolService)).contains("findTools");
        assertThat(methodNames(toolProvider)).contains("isDynamic");
        assertThat(invocationParameters).isNotNull();
    }
    
    private static String[] methodNames(Class<?> type) {
        return Arrays.stream(type.getMethods()).map(Method::getName).toArray(String[]::new);
    }
}
