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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.HttpRequestNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.HttpRequestNodeConfig.*;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP 请求节点执行器
 * 执行 HTTP 请求并返回响应
 * 支持多种认证方式（API Key, Basic Auth, Bearer Token）和重试配置
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HttpNodeExecutor extends AbstractNodeExecutor {

    /**
     * 默认重试状态码
     */
    private static final int[] DEFAULT_RETRY_STATUS_CODES = {429, 500, 502, 503, 504};

    /**
     * 默认连接/读取超时（毫秒），避免慢下游无限挂起工作线程
     */
    private static final int DEFAULT_CONNECT_TIMEOUT_MS = 10_000;
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private final ObjectMapper objectMapper;

    @Override
    public NodeType getType() {
        return NodeType.HTTP_REQUEST;
    }

    @Override
    public void validate(WorkflowNode node) {
        HttpRequestNodeConfig config = parseConfig(node, HttpRequestNodeConfig.class);
        if (config == null || config.getUrl() == null || config.getUrl().isBlank()) {
            throw new IllegalArgumentException("HTTP 请求节点缺少 url 配置");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 HTTP 请求节点: {}", node.getId());

        // 解析强类型配置
        HttpRequestNodeConfig config = parseConfig(node, HttpRequestNodeConfig.class);

        String urlTemplate = config.getUrl();
        if (urlTemplate == null || urlTemplate.isBlank()) {
            return NodeExecutionResult.failure("HTTP 请求节点缺少 url 配置");
        }

        String method = config.getMethod() != null ? config.getMethod().getCode() : "GET";

        Map<String, String> headers = config.getHeaders();

        String bodyTemplate = null;
        if (config.getBody() != null) {
            bodyTemplate = config.getBody() instanceof String ? (String) config.getBody() : objectMapper.valueToTree(config.getBody()).toString();
        }

        boolean parseJsonResponse = config.isParseJsonResponse();
        final String responseType = parseJsonResponse ? "json" : "text";

        // 解析模板
        String url = resolveTemplate(urlTemplate, context);
        String body = bodyTemplate != null ? resolveTemplate(bodyTemplate, context) : null;

        // URL 安全校验（防止 SSRF 攻击）
        String urlValidationError = validateUrl(url, config);
        if (urlValidationError != null) {
            log.warn("HTTP 请求节点 {} 的 URL 校验失败: {}", node.getId(), urlValidationError);
            return NodeExecutionResult.failure("URL 校验失败: " + urlValidationError);
        }

        // 构建请求头
        HttpHeaders httpHeaders = buildHeaders(headers, config.getAuth(), context);

        // 使用专用 RestTemplate：设置连接/读取超时，禁止自动跟随重定向（防止重定向到内网绕过 SSRF 校验），
        // 并且不使用 @LoadBalanced 的负载均衡实例（否则外部 URL 会被当作 Nacos 服务名解析）
        RestTemplate restTemplate = buildRestTemplate(config);

        // 执行请求（带重试）
        RetryConfig retryConfig = config.getRetry();
        int maxRetries = retryConfig != null && retryConfig.isEnabled() ? (retryConfig.getMaxRetries() != null ? retryConfig.getMaxRetries() : 3) : 0;
        int retryInterval = retryConfig != null && retryConfig.getRetryInterval() != null ? retryConfig.getRetryInterval() : 1000;
        double backoffMultiplier = retryConfig != null && retryConfig.getBackoffMultiplier() != null ? retryConfig.getBackoffMultiplier() : 2.0;
        int[] retryStatusCodes = retryConfig != null && retryConfig.getRetryStatusCodes() != null ? retryConfig.getRetryStatusCodes() : DEFAULT_RETRY_STATUS_CODES;

        Exception lastException = null;
        ResponseEntity<String> response = null;

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    long sleepTime = (long) (retryInterval * Math.pow(backoffMultiplier, attempt - 1));
                    log.debug("第 {} 次重试将在 {}ms 后开始", attempt, sleepTime);
                    Thread.sleep(sleepTime);
                }

                HttpEntity<String> requestEntity = new HttpEntity<>(body, httpHeaders);
                org.springframework.http.HttpMethod httpMethod = org.springframework.http.HttpMethod.valueOf(method.toUpperCase());
                response = restTemplate.exchange(url, httpMethod, requestEntity, String.class);

                // 检查是否需要重试
                if (!shouldRetry(response.getStatusCode().value(), retryStatusCodes)) {
                    break;
                }

                log.debug("收到可重试状态码: {}", response.getStatusCode().value());

            } catch (Exception e) {
                lastException = e;
                log.warn("HTTP 请求第 {} 次尝试失败: {}", attempt + 1, e.getMessage());

                if (attempt >= maxRetries) {
                    break;
                }
            }
        }

        if (response == null) {
            String errorMsg = lastException != null ? lastException.getMessage() : "Unknown error";
            log.error("HTTP 请求节点 {} 执行失败: {}", node.getId(), errorMsg);
            return NodeExecutionResult.failure("HTTP 请求失败: " + errorMsg);
        }

        // 解析响应
        String responseBody = response.getBody();
        Object parsedResponse = parseResponse(responseBody, parseJsonResponse ? "json" : responseType);

        // 构建输出
        Map<String, Object> outputs = new HashMap<>();
        outputs.put("result", parsedResponse);
        outputs.put("body", responseBody);
        outputs.put("statusCode", response.getStatusCode().value());
        outputs.put("headers", response.getHeaders().toSingleValueMap());

        log.debug("HTTP 请求节点 {} 执行完成，状态码: {}", node.getId(), response.getStatusCode());
        return NodeExecutionResult.success(outputs);
    }

    /**
     * 构建请求头（包含认证信息）
     * @param auth auth 参数
     * @param context 执行上下文
     * @param headers headers 参数
     * @return 处理结果
     * @throws IllegalArgumentException 认证类型不受支持时抛出
     */
    private HttpHeaders buildHeaders(Map<String, String> headers, AuthConfig auth, ExecutionContext context) {
        HttpHeaders httpHeaders = new HttpHeaders();

        // 添加自定义请求头
        if (headers != null) {
            headers.forEach((key, value) -> {
                String resolvedValue = resolveTemplate(value, context);
                httpHeaders.add(key, resolvedValue);
            });
        }

        // 设置默认 Content-Type
        if (httpHeaders.getContentType() == null) {
            httpHeaders.setContentType(MediaType.APPLICATION_JSON);
        }

        // 添加认证信息
        if (auth != null && auth.getType() != null && auth.getType() != AuthType.NONE) {
            switch (auth.getType()) {
                case API_KEY -> {
                    String headerName = auth.getApiKeyHeader() != null ? auth.getApiKeyHeader() : "Authorization";
                    String prefix = auth.getApiKeyPrefix() != null ? auth.getApiKeyPrefix() + " " : "";
                    httpHeaders.add(headerName, prefix + auth.getApiKey());
                }
                case BASIC -> {
                    String credentials = auth.getUsername() + ":" + auth.getPassword();
                    String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
                    httpHeaders.add(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                }
                case BEARER -> {
                    httpHeaders.add(HttpHeaders.AUTHORIZATION, "Bearer " + auth.getBearerToken());
                }
                default -> throw new IllegalArgumentException("不支持的认证类型: " + auth.getType());
            }
        }

        return httpHeaders;
    }

    /**
     * 检查是否应该重试
     * @param retryStatusCodes retryStatusCodes 参数
     * @param statusCode statusCode 参数
     * @return 处理结果
     */
    private boolean shouldRetry(int statusCode, int[] retryStatusCodes) {
        for (int code : retryStatusCodes) {
            if (code == statusCode) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析响应内容
     * @param responseBody responseBody 参数
     * @param responseType responseType 参数
     * @return 处理结果
     */
    private Object parseResponse(String responseBody, String responseType) {
        if (responseBody == null || responseBody.isEmpty()) {
            return null;
        }

        try {
            switch (responseType.toLowerCase()) {
                case "json" -> {
                    return objectMapper.readValue(responseBody, Object.class);
                }
                case "text" -> {
                    return responseBody;
                }
                default -> {
                    try {
                        return objectMapper.readValue(responseBody, Object.class);
                    } catch (Exception e) {
                        return responseBody;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("响应内容按 {} 解析失败: {}", responseType, e.getMessage());
            return responseBody;
        }
    }

    /**
     * 校验 URL 安全性（防止 SSRF 攻击）
     *
     * @param url    请求 URL
     * @param config 配置
     * @return 错误信息，null 表示校验通过
     */
    private String validateUrl(String url, HttpRequestNodeConfig config) {
        if (url == null || url.isEmpty()) {
            return "URL 为空";
        }

        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
                return "仅支持 http/https 协议: " + scheme;
            }
            String host = uri.getHost();

            if (host == null || host.isEmpty()) {
                return "URL 无效：缺少主机名";
            }

            // 检查是否允许内网地址
            if (!config.isAllowPrivateNetwork()) {
                if (isPrivateNetworkHost(host)) {
                    return "不允许请求内网地址: " + host;
                }
            }

            // 检查黑名单
            String[] blockedDomains = config.getBlockedDomains();
            if (blockedDomains != null) {
                for (String blocked : blockedDomains) {
                    if (matchesDomain(host, blocked)) {
                        return "域名已被禁止: " + host;
                    }
                }
            }

            // 检查白名单（如果配置了白名单，则只允许白名单中的域名）
            String[] allowedDomains = config.getAllowedDomains();
            if (allowedDomains != null && allowedDomains.length > 0) {
                boolean allowed = false;
                for (String allowedDomain : allowedDomains) {
                    if (matchesDomain(host, allowedDomain)) {
                        allowed = true;
                        break;
                    }
                }
                if (!allowed) {
                    return "域名不在白名单中: " + host;
                }
            }

            // 校验通过
            return null;
        } catch (Exception e) {
            return "URL 格式无效: " + e.getMessage();
        }
    }

    /**
     * 检查是否是内网/受限地址。
     * 关键加固：不再只做字符串前缀匹配（会漏掉云元数据 169.254.169.254、0.0.0.0、
     * 十进制/十六进制 IP、IPv6 ULA/link-local 等），而是把主机名解析为真实 IP，
     * 再用 {@link InetAddress} 的标准判定覆盖 loopback / link-local / site-local / anyLocal / 组播，
     * 并显式拦截 169.254.0.0/16（含云厂商 IMDS）。任一解析结果命中即视为内网。
     * @param host host 参数
     * @return 处理结果
     */
    private boolean isPrivateNetworkHost(String host) {
        String normalized = host;
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        try {
            InetAddress[] addresses = InetAddress.getAllByName(normalized);
            for (InetAddress address : addresses) {
                if (isRestrictedAddress(address)) {
                    return true;
                }
            }
            return false;
        } catch (UnknownHostException e) {
            // 无法解析的主机名一律视为不安全，拒绝请求
            log.warn("无法解析主机名，按内网/非法地址拒绝: {}", host);
            return true;
        }
    }

    /**
     * 判断解析后的 IP 是否属于受限网段。
     * @param address address 参数
     * @return 处理结果
     */
    private boolean isRestrictedAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isAnyLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        // 169.254.0.0/16：云厂商元数据服务（AWS/阿里云/GCP IMDS），isLinkLocalAddress 已覆盖，此处双保险
        if (bytes.length == 4) {
            int first = bytes[0] & 0xFF;
            int second = bytes[1] & 0xFF;
            if (first == 169 && second == 254) {
                return true;
            }
            // 100.64.0.0/10 CGNAT
            if (first == 100 && (second & 0xC0) == 0x40) {
                return true;
            }
        }
        return false;
    }

    /**
     * 构建专用 RestTemplate：设置超时并禁止自动跟随重定向。
     * @param config 节点配置
     * @return 处理结果
     */
    private RestTemplate buildRestTemplate(HttpRequestNodeConfig config) {
        int connectTimeout = config.getConnectTimeout() != null && config.getConnectTimeout() > 0
                ? config.getConnectTimeout()
                : DEFAULT_CONNECT_TIMEOUT_MS;
        int readTimeout = config.getReadTimeout() != null && config.getReadTimeout() > 0
                ? config.getReadTimeout()
                : DEFAULT_READ_TIMEOUT_MS;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {

            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                super.prepareConnection(connection, httpMethod);
                // 禁止自动重定向，避免 302 跳转到内网绕过 URL 校验
                connection.setInstanceFollowRedirects(false);
            }
        };
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);
        return new RestTemplate(factory);
    }

    /**
     * 检查域名是否匹配模式（支持通配符）
     * @param host host 参数
     * @param pattern pattern 参数
     * @return 处理结果
     */
    private boolean matchesDomain(String host, String pattern) {
        if (pattern == null || pattern.isEmpty()) {
            return false;
        }

        // 精确匹配
        if (host.equalsIgnoreCase(pattern)) {
            return true;
        }

        // 通配符匹配: *.example.com
        if (pattern.startsWith("*.")) {
            // .example.com
            String suffix = pattern.substring(1);
            return host.toLowerCase().endsWith(suffix.toLowerCase());
        }

        return false;
    }
}
