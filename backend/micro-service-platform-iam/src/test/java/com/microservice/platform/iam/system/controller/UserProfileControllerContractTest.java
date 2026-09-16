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

package com.microservice.platform.iam.system.controller;

import com.microservice.framework.commons.JacksonUtils;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.iam.system.domain.dto.req.UserPasswordUpdateReq;
import com.microservice.platform.iam.system.domain.dto.req.UserProfileUpdateReq;
import org.junit.jupiter.api.Test;
import org.hibernate.validator.constraints.Length;
import org.springframework.web.bind.annotation.PutMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileControllerContractTest {

    @Test
    void exposesCurrentUserProfileRouteWithoutPathUserId() throws NoSuchMethodException {
        Method method = UserController.class.getDeclaredMethod("updateProfile", UserProfileUpdateReq.class);

        assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly("/profile");
    }

    @Test
    void exposesCurrentUserPasswordRouteWithoutPathUserId() throws NoSuchMethodException {
        Method method = UserController.class.getDeclaredMethod("updatePassword", UserPasswordUpdateReq.class);

        assertThat(method.getAnnotation(PutMapping.class).value()).containsExactly("/password");
    }

    @Test
    void passwordEndpointDoesNotPersistRequestOrResponsePayloads() throws NoSuchMethodException {
        Method method = UserController.class.getDeclaredMethod("updatePassword", UserPasswordUpdateReq.class);

        AccessLog accessLog = method.getAnnotation(AccessLog.class);
        assertThat(accessLog.request()).isFalse();
        assertThat(accessLog.response()).isFalse();
    }

    @Test
    void passwordRequestDoesNotExposeSecretsDuringSerialization() {
        UserPasswordUpdateReq req = new UserPasswordUpdateReq();
        req.setCurrentPassword("OldPassword@1");
        req.setNewPassword("NewPassword@2");
        req.setConfirmPassword("NewPassword@2");

        assertThat(JacksonUtils.toJson(req)).isEqualTo("{}");
    }

    @Test
    void profileLengthsMatchDatabaseColumns() throws NoSuchFieldException {
        Length nicknameLength = UserProfileUpdateReq.class.getDeclaredField("nickname").getAnnotation(Length.class);
        Length emailLength = UserProfileUpdateReq.class.getDeclaredField("email").getAnnotation(Length.class);

        assertThat(nicknameLength.max()).isEqualTo(50);
        assertThat(emailLength.max()).isEqualTo(50);
    }
}
