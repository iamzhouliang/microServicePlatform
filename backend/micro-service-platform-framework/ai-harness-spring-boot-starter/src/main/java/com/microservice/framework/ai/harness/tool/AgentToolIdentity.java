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

/**
 * Tool 在跨轮恢复和审计中的稳定身份，由名称与版本共同确定。
 *
 * @param name Tool 名称
 * @param version Tool 契约版本
 * @author xJh
 * @since 2026-07-18
 */
public record AgentToolIdentity(String name, String version) {

    public AgentToolIdentity {
        name = requireText(name, "name");
        version = requireText(version, "version");
    }

    public static AgentToolIdentity parse(String identifier) {
        if (identifier == null) {
            return null;
        }
        int separator = identifier.lastIndexOf('@');
        if (separator <= 0 || separator == identifier.length() - 1) {
            throw new IllegalArgumentException("工具标识必须包含精确名称和版本: " + identifier);
        }
        return new AgentToolIdentity(identifier.substring(0, separator), identifier.substring(separator + 1));
    }

    public String identifier() {
        return name + "@" + version;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("工具标识字段 " + field + " 不能为空");
        }
        return value.trim();
    }

}
