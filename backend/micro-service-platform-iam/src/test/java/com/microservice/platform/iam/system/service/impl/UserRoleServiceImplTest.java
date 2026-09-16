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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.wrap.query.LbqWrapper;
import com.microservice.platform.iam.system.domain.entity.Role;
import com.microservice.platform.iam.system.domain.entity.User;
import com.microservice.platform.iam.system.domain.entity.UserRole;
import com.microservice.platform.iam.system.repository.RoleMapper;
import com.microservice.platform.iam.system.repository.UserMapper;
import com.microservice.platform.iam.system.repository.UserRoleMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户角色原子替换与锁定顺序测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
@ExtendWith(MockitoExtension.class)
class UserRoleServiceImplTest {
    
    @Mock
    private UserRoleMapper userRoleMapper;
    
    @Mock
    private UserMapper userMapper;
    
    @Mock
    private RoleMapper roleMapper;
    
    private UserRoleServiceImpl service;
    
    @BeforeAll
    static void initializeTableMetadata() {
        initTableInfo(User.class);
        initTableInfo(Role.class);
        initTableInfo(UserRole.class);
    }
    
    @BeforeEach
    void setUp() {
        service = new UserRoleServiceImpl(userMapper, roleMapper);
        ReflectionTestUtils.setField(service, "baseMapper", userRoleMapper);
    }
    
    @Test
    void replacesRolesOnlyAfterLockingUserAndMatchingExpectedState() {
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(User.builder().id(91L).build()));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(91L).roleId(22L).build()));
        
        List<Long> previous = service.replaceRolesForUser(91L, List.of(7L), List.of(22L));
        
        assertThat(previous).containsExactly(22L);
        ArgumentCaptor<LbqWrapper<Role>> roleLock = wrapperCaptor();
        ArgumentCaptor<LbqWrapper<User>> userLock = wrapperCaptor();
        verify(roleMapper).selectList(roleLock.capture());
        verify(userMapper).selectList(userLock.capture());
        assertThat(roleLock.getValue().getCustomSqlSegment()).containsIgnoringCase("FOR UPDATE");
        assertThat(userLock.getValue().getCustomSqlSegment()).containsIgnoringCase("FOR UPDATE");
        ArgumentCaptor<LbqWrapper<UserRole>> roleStateReads = wrapperCaptor();
        verify(userRoleMapper, org.mockito.Mockito.times(2)).selectList(roleStateReads.capture());
        assertThat(roleStateReads.getAllValues().getLast().getCustomSqlSegment())
                .containsIgnoringCase("FOR UPDATE");
        var lockOrder = inOrder(roleMapper, userMapper);
        lockOrder.verify(roleMapper).selectList(any(Wrapper.class));
        lockOrder.verify(userMapper).selectList(any(Wrapper.class));
        verify(userRoleMapper).delete(any(Wrapper.class));
        verify(userRoleMapper).insertBatchSomeColumn(any());
    }
    
    @Test
    void rejectsStaleExpectedStateWithoutMutatingRelations() {
        when(userMapper.selectList(any(Wrapper.class))).thenReturn(List.of(User.builder().id(91L).build()));
        when(userRoleMapper.selectList(any(Wrapper.class))).thenReturn(List.of(
                UserRole.builder().userId(91L).roleId(99L).build()));
        
        assertThatThrownBy(() -> service.replaceRolesForUser(91L, List.of(7L), List.of(22L)))
                .isInstanceOf(CheckedException.class)
                .hasMessage("用户角色已被后续操作修改，拒绝覆盖并转人工处理");
        
        verify(userRoleMapper, never()).delete(any(Wrapper.class));
        verify(userRoleMapper, never()).insertBatchSomeColumn(any());
    }
    
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static <T> ArgumentCaptor<LbqWrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LbqWrapper.class);
    }
    
    private static void initTableInfo(Class<?> entityType) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), entityType);
    }
}
