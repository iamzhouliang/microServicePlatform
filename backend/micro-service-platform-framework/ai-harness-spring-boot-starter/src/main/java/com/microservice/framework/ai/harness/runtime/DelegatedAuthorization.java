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

import dev.langchain4j.invocation.InvocationParameters;
import java.util.Optional;

/**
 * 随 LangChain4j 官方调用参数传递的委托令牌。
 *
 * <p>该值只存在于单次模型调用内，不写入对话、Operation、日志或工具返回。</p>
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class DelegatedAuthorization {
    
    public static final String PARAMETER_KEY = DelegatedAuthorization.class.getName();
    
    private final String token;
    
    public DelegatedAuthorization(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("委托授权信息不能为空");
        }
        this.token = token.trim();
    }
    
    public String token() {
        return token;
    }
    
    public static Optional<DelegatedAuthorization> from(InvocationParameters parameters) {
        if (parameters == null) {
            return Optional.empty();
        }
        Object value = parameters.get(PARAMETER_KEY);
        return value instanceof DelegatedAuthorization authorization
                ? Optional.of(authorization)
                : Optional.empty();
    }
    
    @Override
    public String toString() {
        return "DelegatedAuthorization[已脱敏]";
    }
}
