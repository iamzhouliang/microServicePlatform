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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.harness.runtime.DelegatedAuthorizationProvider;
import com.microservice.framework.ai.harness.runtime.HarnessVerification;
import com.microservice.framework.ai.harness.runtime.HarnessVerifier;
import com.microservice.framework.ai.harness.runtime.model.HarnessOperation;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import java.util.Objects;

/**
 * IAM 原子用户开通的权威回读 Verifier。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class IamProvisionResultVerifier implements HarnessVerifier {

    private static final String BEARER_PREFIX = "Bearer ";

    private final IamAgentFeign feign;
    private final DelegatedAuthorizationProvider authorizationProvider;
    private final ObjectMapper objectMapper;

    public IamProvisionResultVerifier(IamAgentFeign feign,
                                      DelegatedAuthorizationProvider authorizationProvider, ObjectMapper objectMapper) {
        this.feign = Objects.requireNonNull(feign, "IAM Feign 不能为空");
        this.authorizationProvider = Objects.requireNonNull(authorizationProvider, "委托授权提供器不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    @Override
    public HarnessVerification verify(HarnessOperation operation) {
        try {
            var result = feign.getProvisionResult(authorization(), operation.operationId());
            return HarnessVerification.confirmed(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException failure) {
            return HarnessVerification.unconfirmed("IAM 权威回读结果无法序列化");
        } catch (RuntimeException failure) {
            String message = failure.getMessage() == null || failure.getMessage().isBlank()
                    ? "IAM 权威回读未返回可确认结果"
                    : failure.getMessage();
            return HarnessVerification.unconfirmed(message);
        }
    }

    private String authorization() {
        String value = authorizationProvider.current().token();
        return value.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())
                ? value
                : BEARER_PREFIX + value;
    }
}
