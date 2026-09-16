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

package com.microservice.platform.gateway.rest;

import com.github.xingfudeshi.knife4j.spring.gateway.Knife4jGatewayProperties;
import com.github.xingfudeshi.knife4j.spring.gateway.enums.GatewayStrategy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spring Boot 4 compatible Knife4j gateway swagger config endpoint.
 *
 * @author Levin
 */
@RestController
@EnableConfigurationProperties(Knife4jGatewayProperties.class)
@ConditionalOnProperty(name = "knife4j.gateway.enabled", havingValue = "true")
public class Knife4jSwaggerConfigController {
    
    private static final String DEFAULT_CONTEXT_PATH = "/";
    private static final String SWAGGER_CONFIG_PATH = "/v3/api-docs/swagger-config";
    private static final Pattern DOC_HTML_PATTERN = Pattern.compile("(.*?)/doc\\.html", Pattern.CASE_INSENSITIVE);
    
    private final Knife4jGatewayProperties properties;
    
    public Knife4jSwaggerConfigController(Knife4jGatewayProperties properties) {
        this.properties = properties;
    }
    
    @GetMapping(SWAGGER_CONFIG_PATH)
    public Mono<ResponseEntity<SwaggerConfigResponse>> swaggerConfig(ServerHttpRequest request) {
        String basePath = defaultContextPath(request);
        List<SwaggerResource> urls = properties.getStrategy() == GatewayStrategy.MANUAL ? manualResources(basePath) : List.of();
        Knife4jGatewayProperties.OpenApiV3 oas3 = properties.getDiscover().getOas3();
        SwaggerConfigResponse response = new SwaggerConfigResponse(SWAGGER_CONFIG_PATH,
                oas3.getOauth2RedirectUrl(), properties.getOperationsSorter().name(),
                properties.getTagsSorter().name(), urls, oas3.getValidatorUrl());
        return Mono.just(ResponseEntity.ok(response));
    }
    
    private List<SwaggerResource> manualResources(String basePath) {
        return properties.getRoutes().stream()
                .sorted(Comparator.comparing(Knife4jGatewayProperties.Router::getOrder))
                .map(router -> {
                    String url = append(basePath, router.getUrl());
                    String contextPath = processContextPath(append(basePath, router.getContextPath()));
                    String id = resourceId(router.getName(), router.getUrl(), router.getContextPath());
                    return new SwaggerResource(router.getName(), url, contextPath, id,
                            router.getOrder(), false, router.getServiceName());
                })
                .toList();
    }
    
    private static String defaultContextPath(ServerHttpRequest request) {
        String contextPath = request.getPath().contextPath().value();
        if (StringUtils.hasLength(contextPath)) {
            return contextPath;
        }
        String referer = request.getHeaders().getFirst(HttpHeaders.REFERER);
        if (!StringUtils.hasLength(referer)) {
            return DEFAULT_CONTEXT_PATH;
        }
        try {
            Matcher matcher = DOC_HTML_PATTERN.matcher(java.net.URI.create(referer).getPath());
            return matcher.find() ? matcher.group(1) : DEFAULT_CONTEXT_PATH;
        } catch (IllegalArgumentException ex) {
            return DEFAULT_CONTEXT_PATH;
        }
    }
    
    private static String append(String... paths) {
        StringBuilder fullPath = new StringBuilder();
        for (String path : paths) {
            if (!StringUtils.hasLength(path)) {
                continue;
            }
            if (!path.startsWith(DEFAULT_CONTEXT_PATH)) {
                fullPath.append(DEFAULT_CONTEXT_PATH);
            }
            fullPath.append(path);
        }
        return fullPath.toString().replaceAll("/+", DEFAULT_CONTEXT_PATH);
    }
    
    private static String processContextPath(String contextPath) {
        if (DEFAULT_CONTEXT_PATH.equals(contextPath)) {
            return "";
        }
        if (contextPath.endsWith(DEFAULT_CONTEXT_PATH)) {
            return contextPath.substring(0, contextPath.length() - 1);
        }
        return contextPath;
    }
    
    private static String resourceId(String name, String url, String contextPath) {
        return Base64.getEncoder().encodeToString((name + url + contextPath).getBytes(StandardCharsets.UTF_8));
    }
    
    public record SwaggerConfigResponse(String configUrl,
                                        String oauth2RedirectUrl,
                                        String operationsSorter,
                                        String tagsSorter,
                                        List<SwaggerResource> urls,
                                        String validatorUrl) {
    }
    
    public record SwaggerResource(String name,
                                  String url,
                                  String contextPath,
                                  String id,
                                  Integer order,
                                  Boolean discovered,
                                  String serviceName) {
    }
    
}
