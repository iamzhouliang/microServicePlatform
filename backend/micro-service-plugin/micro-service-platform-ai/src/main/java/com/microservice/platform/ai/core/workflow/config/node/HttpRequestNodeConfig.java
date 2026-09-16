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

package com.microservice.platform.ai.core.workflow.config.node;

import com.microservice.platform.ai.core.enums.AiEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * HTTP 请求节点配置
 * 发送 HTTP 请求，支持多种认证方式和重试配置
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HttpRequestNodeConfig {

    /**
     * 请求 URL
     * 支持变量引用: {{nodeName.variableName}}
     */
    private String url;

    /**
     * 请求方法
     */
    private HttpMethod method;

    /**
     * 请求头
     * 支持变量引用
     */
    private Map<String, String> headers;

    /**
     * 查询参数
     * 支持变量引用
     */
    private Map<String, String> queryParams;

    /**
     * 请求体类型
     */
    private BodyType bodyType;

    /**
     * 请求体内容
     * 根据 bodyType 解析
     */
    private Object body;

    /**
     * 认证配置
     */
    private AuthConfig auth;

    /**
     * 连接超时（毫秒）
     */
    private Integer connectTimeout;

    /**
     * 读取超时（毫秒）
     */
    private Integer readTimeout;

    /**
     * 重试配置
     */
    private RetryConfig retry;

    /**
     * 是否验证 SSL 证书
     */
    private boolean sslVerify;

    /**
     * 输出变量名
     */
    private String outputVariable;

    /**
     * 是否解析 JSON 响应
     */
    private boolean parseJsonResponse;

    /**
     * URL 白名单（可选）
     * 如果配置，则只允许请求白名单中的域名
     * 支持通配符，如: *.example.com
     */
    private String[] allowedDomains;

    /**
     * URL 黑名单（可选）
     * 禁止请求的域名列表
     * 默认禁止内网地址: localhost, 127.0.0.1, 10.*, 172.16.*, 192.168.*
     */
    private String[] blockedDomains;

    /**
     * 是否允许请求内网地址
     * 默认: false（禁止 SSRF 攻击）
     */
    private boolean allowPrivateNetwork;

    /**
     * HTTP 方法枚举
     */
    @Getter
    @AllArgsConstructor
    public enum HttpMethod implements AiEnum {

        GET("GET"),
        POST("POST"),
        PUT("PUT"),
        DELETE("DELETE"),
        PATCH("PATCH"),
        HEAD("HEAD"),
        OPTIONS("OPTIONS");

        private final String code;
    }

    /**
     * 请求体类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum BodyType implements AiEnum {

        NONE("NONE", "无请求体"),
        JSON("JSON", "JSON"),
        FORM_DATA("FORM_DATA", "表单数据"),
        X_WWW_FORM_URLENCODED("X_WWW_FORM_URLENCODED", "URL 编码表单"),
        RAW("RAW", "原始文本"),
        BINARY("BINARY", "二进制");

        private final String code;
        private final String description;
    }

    /**
     * 认证配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthConfig {

        /**
         * 认证类型
         */
        private AuthType type;

        /**
         * API Key 值
         */
        private String apiKey;

        /**
         * API Key 请求头名称
         * 默认: Authorization
         */
        private String apiKeyHeader;

        /**
         * API Key 前缀
         * 如: Bearer, Token
         */
        private String apiKeyPrefix;

        /**
         * Basic Auth 用户名
         */
        private String username;

        /**
         * Basic Auth 密码
         */
        private String password;

        /**
         * Bearer Token
         */
        private String bearerToken;
    }

    /**
     * 认证类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum AuthType implements AiEnum {

        NONE("NONE", "无认证"),
        API_KEY("API_KEY", "API Key"),
        BASIC("BASIC", "Basic Auth"),
        BEARER("BEARER", "Bearer Token");

        private final String code;
        private final String description;
    }

    /**
     * 重试配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RetryConfig {

        /**
         * 是否启用重试
         */
        private boolean enabled;

        /**
         * 最大重试次数
         */
        private Integer maxRetries;

        /**
         * 重试间隔（毫秒）
         */
        private Integer retryInterval;

        /**
         * 退避乘数
         * 每次重试间隔 = 上次间隔 * backoffMultiplier
         */
        private Double backoffMultiplier;

        /**
         * 需要重试的 HTTP 状态码
         * 默认: 429, 500, 502, 503, 504
         */
        private int[] retryStatusCodes;
    }
}
