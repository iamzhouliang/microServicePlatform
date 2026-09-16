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

package com.microservice.platform.iam.system.service.impl;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.datascope.service.DataScopeService;
import com.microservice.framework.db.properties.DatabaseProperties;
import com.microservice.framework.security.configuration.SecurityExtProperties;
import com.microservice.framework.security.domain.UserInfoDetails;
import com.microservice.framework.security.utils.PasswordEncoderHelper;
import com.microservice.platform.iam.system.domain.dto.req.UserPasswordUpdateReq;
import com.microservice.platform.iam.system.domain.dto.req.UserProfileUpdateReq;
import com.microservice.platform.iam.system.domain.entity.User;
import com.microservice.platform.iam.system.repository.ResourceMapper;
import com.microservice.platform.iam.system.repository.RoleMapper;
import com.microservice.platform.iam.system.repository.UserMapper;
import com.microservice.platform.iam.system.repository.UserRoleMapper;
import com.microservice.platform.iam.system.service.OrgService;
import com.microservice.platform.iam.tenant.repository.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserProfileServiceTest {

    private static final Long CURRENT_USER_ID = 42L;
    private static final String CURRENT_PASSWORD = "OldPassword@1";
    private static final String NEW_PASSWORD = "NewPassword@2";

    @Mock
    private UserMapper userMapper;
    @Mock
    private UserRoleMapper userRoleMapper;
    @Mock
    private AuthenticationContext context;
    @Mock
    private OrgService orgService;
    @Mock
    private ResourceMapper resourceMapper;
    @Mock
    private RoleMapper roleMapper;
    @Mock
    private TenantMapper tenantMapper;
    @Mock
    private DataScopeService dataScopeService;
    @Mock
    private SaTokenDao saTokenDao;
    @Mock
    private DatabaseProperties databaseProperties;

    private SecurityExtProperties securityExtProperties;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() {
        securityExtProperties = new SecurityExtProperties();
        service = new UserServiceImpl(userRoleMapper, context, orgService, resourceMapper, roleMapper,
                tenantMapper, dataScopeService, saTokenDao, securityExtProperties, databaseProperties);
        ReflectionTestUtils.setField(service, "baseMapper", userMapper);
    }

    @Test
    void updateProfileUsesAuthenticatedUserAndRefreshesSession() {
        when(context.userId()).thenReturn(CURRENT_USER_ID);
        UserProfileUpdateReq req = new UserProfileUpdateReq();
        req.setNickname("新昵称");
        req.setEmail("profile@example.com");
        req.setMobile("13000000000");
        req.setDescription("新的个人简介");
        UserInfoDetails userInfo = UserInfoDetails.builder().userId(CURRENT_USER_ID).build();
        SaSession tokenSession = mock(SaSession.class);
        when(context.getContext()).thenReturn(userInfo);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getTokenSession).thenReturn(tokenSession);

            service.updateProfile(req);
        }

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(CURRENT_USER_ID);
        assertThat(updated.getNickname()).isEqualTo(req.getNickname());
        assertThat(updated.getEmail()).isEqualTo(req.getEmail());
        assertThat(updated.getMobile()).isEqualTo(req.getMobile());
        assertThat(updated.getDescription()).isEqualTo(req.getDescription());
        assertThat(userInfo.getNickname()).isEqualTo(req.getNickname());
        assertThat(userInfo.getEmail()).isEqualTo(req.getEmail());
        assertThat(userInfo.getMobile()).isEqualTo(req.getMobile());
        assertThat(userInfo.getDescription()).isEqualTo(req.getDescription());
        verify(tokenSession).set(securityExtProperties.getServer().getTokenInfoKey(), userInfo);
    }

    @Test
    void updatePasswordValidatesCurrentPasswordAndStoresNewHash() {
        when(context.userId()).thenReturn(CURRENT_USER_ID);
        UserPasswordUpdateReq req = passwordRequest(CURRENT_PASSWORD);
        User user = User.builder().id(CURRENT_USER_ID)
                .password(PasswordEncoderHelper.encode(CURRENT_PASSWORD)).build();
        when(userMapper.selectById(CURRENT_USER_ID)).thenReturn(user);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            service.updatePassword(req);

            stpUtil.verify(() -> StpUtil.logout(CURRENT_USER_ID));
        }

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User updated = captor.getValue();
        assertThat(updated.getId()).isEqualTo(CURRENT_USER_ID);
        assertThat(updated.getPassword()).isNotEqualTo(NEW_PASSWORD);
        assertThat(PasswordEncoderHelper.matches(NEW_PASSWORD, updated.getPassword())).isTrue();
    }

    @Test
    void updatePasswordRejectsIncorrectCurrentPassword() {
        when(context.userId()).thenReturn(CURRENT_USER_ID);
        UserPasswordUpdateReq req = passwordRequest("WrongPassword@9");
        User user = User.builder().id(CURRENT_USER_ID)
                .password(PasswordEncoderHelper.encode(CURRENT_PASSWORD)).build();
        when(userMapper.selectById(CURRENT_USER_ID)).thenReturn(user);

        assertThatThrownBy(() -> service.updatePassword(req))
                .isInstanceOf(CheckedException.class)
                .hasMessage("原始密码错误");
        verify(userMapper, never()).updateById(any(User.class));
    }

    @Test
    void updatePasswordRejectsMismatchedConfirmation() {
        UserPasswordUpdateReq req = passwordRequest(CURRENT_PASSWORD);
        req.setConfirmPassword("DifferentPassword@3");

        assertThatThrownBy(() -> service.updatePassword(req))
                .isInstanceOf(CheckedException.class)
                .hasMessage("新密码与确认密码不一致");
        verify(userMapper, never()).selectById(any());
        verify(userMapper, never()).updateById(any(User.class));
    }

    private static UserPasswordUpdateReq passwordRequest(String currentPassword) {
        UserPasswordUpdateReq req = new UserPasswordUpdateReq();
        req.setCurrentPassword(currentPassword);
        req.setNewPassword(NEW_PASSWORD);
        req.setConfirmPassword(NEW_PASSWORD);
        return req;
    }
}
