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

package com.microservice.framework.feign.plugin;

import feign.slf4j.Slf4jLogger;
import java.util.Locale;
import java.util.Set;

/**
 * 过滤认证凭证的 Feign 日志实现。
 *
 * <p>即使开发环境显式启用 HEADERS 或 FULL，也不会输出认证头、Cookie 或系统级服务令牌。</p>
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class SensitiveHeaderFilteringFeignLogger extends Slf4jLogger {

    private static final Set<String> SENSITIVE_HEADERS = Set.of(
            "authorization", "proxy-authorization", "cookie", "set-cookie",
            "sa-same-token", "x-api-key", "x-auth-token");

    public SensitiveHeaderFilteringFeignLogger(Class<?> type) {
        super(type);
    }

    @Override
    protected boolean shouldLogRequestHeader(String header) {
        return !isSensitive(header);
    }

    @Override
    protected boolean shouldLogResponseHeader(String header) {
        return !isSensitive(header);
    }

    private static boolean isSensitive(String header) {
        return header != null && SENSITIVE_HEADERS.contains(header.toLowerCase(Locale.ROOT));
    }
}
