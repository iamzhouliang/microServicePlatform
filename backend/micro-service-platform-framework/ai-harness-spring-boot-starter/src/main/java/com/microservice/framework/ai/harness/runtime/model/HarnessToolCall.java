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

package com.microservice.framework.ai.harness.runtime.model;

/**
 * Tool 调用审计投影，原始敏感参数由持久化实现加密或脱敏。
 *
 * @param operationId 操作标识
 * @param toolCallId LangChain4j Tool Call 标识
 * @param toolIdentity Tool 稳定身份
 * @param argumentsDigest 参数摘要
 * @param fencingToken 租约围栏令牌
 * @author xJh
 * @since 2026-07-18
 */
public record HarnessToolCall(String operationId, String toolCallId, String toolIdentity,
        String argumentsDigest, long fencingToken) {
}
