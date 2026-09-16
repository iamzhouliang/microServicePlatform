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

package com.microservice.framework.boot.log.aspect;

import com.microservice.framework.boot.log.AccessLogInfo;
import com.microservice.framework.boot.log.AccessLogProperties;
import com.microservice.framework.boot.log.handler.AbstractLogHandler;
import com.microservice.framework.commons.JacksonUtils;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.framework.commons.security.AuthenticationContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author OmX
 */
class AccessLogAspectSecurityTest {

    private static final String TOKEN = "agent-secret-token";
    private static final String CONFIGURED_TOKEN_KEY = "X-Agent-Token";
    private static final String CONFIGURED_HEADER_TOKEN = "configured-header-secret";
    private static final String CONFIGURED_PARAMETER_TOKEN = "configured-parameter-secret";
    private static final String CONFIGURED_ATTRIBUTE_TOKEN = "configured-attribute-secret";
    private static final String PASSWORD = "login-password-secret";
    private static final String CLIENT_SECRET = "login-client-secret";
    private static final String ACCESS_TOKEN = "issued-access-token-secret";
    private static final String REDACTED = "[REDACTED]";

    @AfterEach
    void clearRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void redactsDelegatedTokenFromPersistedAndDebugAuditPayloads() throws Throwable {
        AccessLogAspect aspect = new AccessLogAspect();
        AccessLogProperties properties = new AccessLogProperties();
        CapturingLogHandler handler = new CapturingLogHandler();
        ReflectionTestUtils.setField(aspect, "accessLogProperties", properties);
        ReflectionTestUtils.setField(aspect, "context", mock(AuthenticationContext.class));
        ReflectionTestUtils.setField(aspect, "abstractLogHandler", handler);

        MockHttpServletRequest request = requestWithToken();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint joinPoint = auditedJoinPoint();

        aspect.around(joinPoint);

        assertTrue(handler.await());
        AccessLogInfo persistedInfo = handler.get();
        String persistedPayload = JacksonUtils.toJson(persistedInfo);
        String debugPayload = ReflectionTestUtils.invokeMethod(aspect, "formatDebugLog", joinPoint,
                "Agent creates user", Map.of("echo", TOKEN), 10L, request);

        assertNull(persistedInfo.getToken());
        assertTrue(persistedInfo.getRequest().contains(REDACTED));
        assertFalse(persistedPayload.contains(TOKEN));
        assertFalse(debugPayload.contains(TOKEN));
        assertTrue(debugPayload.contains("Header        :  {Authorization=" + REDACTED + "}"));
        assertTrue(debugPayload.contains("Authorization=" + REDACTED));
    }

    @Test
    void redactsAuthorizationAndEveryConfiguredTokenSource() throws Throwable {
        AccessLogAspect aspect = new AccessLogAspect();
        AccessLogProperties properties = new AccessLogProperties();
        properties.setToken(CONFIGURED_TOKEN_KEY);
        CapturingLogHandler handler = new CapturingLogHandler();
        ReflectionTestUtils.setField(aspect, "accessLogProperties", properties);
        ReflectionTestUtils.setField(aspect, "context", mock(AuthenticationContext.class));
        ReflectionTestUtils.setField(aspect, "abstractLogHandler", handler);

        MockHttpServletRequest request = requestWithConfiguredTokens();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint joinPoint = configuredTokenJoinPoint();

        aspect.around(joinPoint);

        assertTrue(handler.await());
        String persistedPayload = JacksonUtils.toJson(handler.get());
        String debugPayload = ReflectionTestUtils.invokeMethod(aspect, "formatDebugLog", joinPoint,
                "Agent creates user", configuredTokenResponse(), 10L, request);
        for (String sensitiveValue : configuredSensitiveValues()) {
            assertFalse(persistedPayload.contains(sensitiveValue));
            assertFalse(debugPayload.contains(sensitiveValue));
        }
        assertNull(handler.get().getToken());
        assertTrue(debugPayload.contains("Header        :  {Authorization=" + REDACTED + "}"));
        assertTrue(debugPayload.contains(CONFIGURED_TOKEN_KEY + "=" + REDACTED));
    }

    @Test
    void redactsCredentialFieldsEvenWhenTheirValuesAreNotPresentInHeaders() throws Throwable {
        AccessLogAspect aspect = new AccessLogAspect();
        AccessLogProperties properties = new AccessLogProperties();
        CapturingLogHandler handler = new CapturingLogHandler();
        ReflectionTestUtils.setField(aspect, "accessLogProperties", properties);
        ReflectionTestUtils.setField(aspect, "context", mock(AuthenticationContext.class));
        ReflectionTestUtils.setField(aspect, "abstractLogHandler", handler);

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/token/login");
        request.addHeader(HttpHeaders.USER_AGENT, "AccessLogAspectSecurityTest");
        request.setContentType("application/json");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        ProceedingJoinPoint joinPoint = credentialJoinPoint();

        aspect.around(joinPoint);

        assertTrue(handler.await());
        String persistedPayload = JacksonUtils.toJson(handler.get());
        String debugPayload = ReflectionTestUtils.invokeMethod(aspect, "formatDebugLog", joinPoint,
                "用户登录", credentialResponse(), 10L, request);
        for (String secret : new String[]{PASSWORD, CLIENT_SECRET, ACCESS_TOKEN}) {
            assertFalse(persistedPayload.contains(secret), persistedPayload);
            assertFalse(debugPayload.contains(secret), debugPayload);
        }
        assertTrue(debugPayload.contains("[REDACTED]"));
    }

    private MockHttpServletRequest requestWithToken() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/agent/iam/users");
        request.addHeader(HttpHeaders.AUTHORIZATION, TOKEN);
        request.addHeader(HttpHeaders.USER_AGENT, "AccessLogAspectSecurityTest");
        request.setContentType("application/json");
        request.setQueryString("Authorization=" + TOKEN + "&filter=active");
        request.setParameter("Authorization", TOKEN);
        request.setRemoteAddr("127.0.0.1");
        request.setServerName("localhost");
        request.setServerPort(8080);
        return request;
    }

    private MockHttpServletRequest requestWithConfiguredTokens() {
        MockHttpServletRequest request = requestWithToken();
        request.addHeader(CONFIGURED_TOKEN_KEY, CONFIGURED_HEADER_TOKEN);
        request.setQueryString(CONFIGURED_TOKEN_KEY + "=" + CONFIGURED_PARAMETER_TOKEN + "&filter=active");
        request.setParameter(CONFIGURED_TOKEN_KEY, CONFIGURED_PARAMETER_TOKEN);
        request.setAttribute(CONFIGURED_TOKEN_KEY, CONFIGURED_ATTRIBUTE_TOKEN);
        return request;
    }

    private ProceedingJoinPoint auditedJoinPoint() throws Throwable {
        AuditedEndpoint endpoint = new AuditedEndpoint();
        Method method = AuditedEndpoint.class.getDeclaredMethod("createUser", String.class, Map.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(method.getName());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(endpoint);
        when(joinPoint.getArgs()).thenReturn(new Object[]{TOKEN, Map.of("nestedToken", TOKEN)});
        when(joinPoint.proceed()).thenReturn(Map.of("echo", TOKEN));
        return joinPoint;
    }

    private ProceedingJoinPoint configuredTokenJoinPoint() throws Throwable {
        AuditedEndpoint endpoint = new AuditedEndpoint();
        Method method = AuditedEndpoint.class.getDeclaredMethod("createUser", String.class, Map.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(method.getName());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(endpoint);
        when(joinPoint.getArgs()).thenReturn(configuredSensitiveValues());
        when(joinPoint.proceed()).thenReturn(configuredTokenResponse());
        return joinPoint;
    }

    private ProceedingJoinPoint credentialJoinPoint() throws Throwable {
        AuditedEndpoint endpoint = new AuditedEndpoint();
        Method method = AuditedEndpoint.class.getDeclaredMethod("createUser", String.class, Map.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.getName()).thenReturn(method.getName());
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(endpoint);
        when(joinPoint.getArgs()).thenReturn(new Object[]{"admin", Map.of(
                "password", PASSWORD, "clientSecret", CLIENT_SECRET)});
        when(joinPoint.proceed()).thenReturn(credentialResponse());
        return joinPoint;
    }

    private Map<String, Object> credentialResponse() {
        return Map.of("accessToken", ACCESS_TOKEN, "tokenType", "Bearer");
    }

    private String[] configuredSensitiveValues() {
        return new String[]{TOKEN, CONFIGURED_HEADER_TOKEN, CONFIGURED_PARAMETER_TOKEN, CONFIGURED_ATTRIBUTE_TOKEN};
    }

    private Map<String, Object> configuredTokenResponse() {
        return Map.of("authorization", TOKEN, "configuredHeader", CONFIGURED_HEADER_TOKEN,
                "configuredParameter", CONFIGURED_PARAMETER_TOKEN, "configuredAttribute", CONFIGURED_ATTRIBUTE_TOKEN);
    }

    private static final class AuditedEndpoint {

        @AccessLog(module = "IAM Agent", description = "Agent creates user")
        public Map<String, Object> createUser(String authorization, Map<String, Object> request) {
            return request;
        }

    }

    private static final class CapturingLogHandler extends AbstractLogHandler {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final AtomicReference<AccessLogInfo> captured = new AtomicReference<>();

        @Override
        public AccessLogInfo handler(AccessLogInfo info) {
            captured.set(info);
            latch.countDown();
            return info;
        }

        boolean await() throws InterruptedException {
            return latch.await(5, TimeUnit.SECONDS);
        }

        AccessLogInfo get() {
            return captured.get();
        }

    }

}
