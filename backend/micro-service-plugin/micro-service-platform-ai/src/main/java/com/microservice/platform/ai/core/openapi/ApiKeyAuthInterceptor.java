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

package com.microservice.platform.ai.core.openapi;

import com.microservice.platform.ai.domain.entity.workflow.WorkflowApiKey;
import com.microservice.platform.ai.service.WorkflowApiKeyService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * API Key 认证拦截器
 * 拦截 /open-api/** 路径的请求，从请求头中提取 API Key 进行认证
 * 认证通过后将 WorkflowApiKey 实体存入 request attribute，供 Controller 使用
 *
 * @author xJh
 * @since 2026/02/06
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApiKeyAuthInterceptor implements HandlerInterceptor {

    /**
     * request attribute 中存储认证信息的 key
     */
    public static final String ATTR_API_KEY = "openapi.authenticated_api_key";

    private final WorkflowApiKeyService workflowApiKeyService;

    /**
     * 支持的认证头格式：
     * - Authorization: Bearer sk-wf-xxx
     * - X-Api-Key: sk-wf-xxx
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String apiKey = extractApiKey(request);

        if (apiKey == null || apiKey.isBlank()) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Missing API Key. Use 'Authorization: Bearer sk-wf-xxx' or 'X-Api-Key: sk-wf-xxx' header.");
            return false;
        }

        WorkflowApiKey authenticated = workflowApiKeyService.authenticate(apiKey);
        if (authenticated == null) {
            sendError(response, HttpStatus.UNAUTHORIZED, "Invalid or expired API Key.");
            return false;
        }

        // 将认证信息存入 request，供后续 Controller 使用
        request.setAttribute(ATTR_API_KEY, authenticated);
        return true;
    }

    /**
     * 从请求中提取 API Key
     * @param request 请求参数
     * @return 处理结果
     */
    private String extractApiKey(HttpServletRequest request) {
        // 优先从 Authorization 头提取
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7).trim();
        }

        // 其次从 X-Api-Key 头提取
        String xApiKey = request.getHeader("X-Api-Key");
        if (xApiKey != null && !xApiKey.isBlank()) {
            return xApiKey.trim();
        }

        // 最后尝试 query parameter
        return request.getParameter("api_key");
    }

    /**
     * 发送错误响应
     * @param message 消息内容
     * @param response response 参数
     * @param status 状态
     * @throws Exception 处理失败时抛出
     */
    private void sendError(HttpServletResponse response, HttpStatus status, String message) throws Exception {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":" + status.value() + ",\"message\":\"" + message + "\"}");
    }
}
