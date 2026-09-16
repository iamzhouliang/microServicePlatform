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

package com.microservice.platform.iam.auth.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.microservice.platform.iam.auth.support.AuthenticationPrincipal;
import com.microservice.platform.iam.system.repository.RegisteredClientMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * 登录流程日志脱敏测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class AuthenticatorStrategyTemplateSecurityTest {

    @Test
    void prepareShouldNotLogPasswordOrClientSecret() {
        RegisteredClientMapper mapper = mock(RegisteredClientMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);
        AuthenticatorStrategyTemplate template = new AuthenticatorStrategyTemplate(List.of(), mapper);
        AuthenticationPrincipal principal = AuthenticationPrincipal.builder()
                .loginType("password").tenantCode("0000").username("admin")
                .password("login-password-secret").clientId("pc-web")
                .clientSecret("client-secret-value").build();
        Logger logger = (Logger) LoggerFactory.getLogger(AuthenticatorStrategyTemplate.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            catchThrowable(() -> template.prepare(principal));
        } finally {
            logger.detachAppender(appender);
        }

        String messages = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + right);
        assertThat(messages).doesNotContain("login-password-secret", "client-secret-value");
        assertThat(messages).contains("admin", "pc-web");
    }
}
