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

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.iam.system.domain.dto.resp.UserRoleResp;
import com.microservice.platform.iam.system.domain.entity.User;
import com.microservice.platform.iam.system.domain.entity.Role;
import com.microservice.platform.iam.system.domain.entity.UserRole;
import com.microservice.platform.iam.system.repository.RoleMapper;
import com.microservice.platform.iam.system.repository.UserMapper;
import com.microservice.platform.iam.system.repository.UserRoleMapper;
import com.microservice.platform.iam.system.service.UserRoleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 业务实现类
 * 角色分配
 * 账号角色绑定
 * </p>
 *
 * @author Levin
 * @since 2019-07-03
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserRoleServiceImpl extends SuperServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;

    @Override
    public UserRoleResp findUserByRoleId(Long roleId) {
        final List<Long> userIdList = super.list(Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, roleId))
                .stream().map(UserRole::getUserId).distinct().collect(Collectors.toList());
        final List<User> users = userMapper.selectList(Wraps.lbQ());
        if (CollectionUtil.isEmpty(users)) {
            return null;
        }
        final List<UserRoleResp.UserRoleDetail> userRoleDetails = users.stream().map(user -> UserRoleResp.UserRoleDetail.builder()
                .id(user.getId()).nickname(user.getNickname()).username(user.getUsername()).build()).collect(Collectors.toList());
        return UserRoleResp.builder().userRoleDetails(userRoleDetails).originTargetKeys(userIdList).build();
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public List<Long> replaceRolesForUser(Long userId, Collection<Long> roleIds,
                                          Collection<Long> expectedCurrentRoleIds) {
        Long requiredUserId = requireId(userId, "用户 ID");
        List<Long> targetRoleIds = normalizedIds(roleIds, "目标角色 ID");
        Set<Long> affectedRoleIds = new LinkedHashSet<>(baseMapper.selectList(
                Wraps.<UserRole>lbQ().eq(UserRole::getUserId, requiredUserId))
                .stream().map(UserRole::getRoleId).toList());
        affectedRoleIds.addAll(targetRoleIds);
        if (expectedCurrentRoleIds != null) {
            affectedRoleIds.addAll(normalizedIds(expectedCurrentRoleIds, "期望当前角色 ID"));
        }
        lockRoles(affectedRoleIds);
        lockUsers(List.of(requiredUserId));
        List<Long> currentRoleIds = baseMapper.selectList(
                Wraps.<UserRole>lbQ().eq(UserRole::getUserId, requiredUserId).last("FOR UPDATE"))
                .stream().map(UserRole::getRoleId).toList();
        if (expectedCurrentRoleIds != null
                && !sameIds(currentRoleIds, normalizedIds(expectedCurrentRoleIds, "期望当前角色 ID"))) {
            throw CheckedException.badRequest(409, "用户角色已被后续操作修改，拒绝覆盖并转人工处理");
        }
        baseMapper.delete(Wraps.<UserRole>lbQ().eq(UserRole::getUserId, requiredUserId));
        if (!targetRoleIds.isEmpty()) {
            baseMapper.insertBatchSomeColumn(targetRoleIds.stream()
                    .map(roleId -> UserRole.builder().userId(requiredUserId).roleId(roleId).build()).toList());
        }
        return List.copyOf(currentRoleIds);
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void replaceUsersForRole(Long roleId, Collection<Long> userIds) {
        Long requiredRoleId = requireId(roleId, "角色 ID");
        lockRoles(List.of(requiredRoleId));
        List<Long> targetUserIds = normalizedIds(userIds, "用户 ID");
        Set<Long> affectedUserIds = new LinkedHashSet<>(baseMapper.selectList(
                Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, requiredRoleId))
                .stream().map(UserRole::getUserId).toList());
        affectedUserIds.addAll(targetUserIds);
        lockUsers(affectedUserIds);
        baseMapper.delete(Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, requiredRoleId));
        if (!targetUserIds.isEmpty()) {
            baseMapper.insertBatchSomeColumn(targetUserIds.stream()
                    .map(userId -> UserRole.builder().userId(userId).roleId(requiredRoleId).build()).toList());
        }
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void deleteByRole(Long roleId) {
        Long requiredRoleId = requireId(roleId, "角色 ID");
        lockRoles(List.of(requiredRoleId));
        List<Long> affectedUserIds = baseMapper.selectList(
                Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, requiredRoleId))
                .stream().map(UserRole::getUserId).distinct().toList();
        lockUsers(affectedUserIds);
        baseMapper.delete(Wraps.<UserRole>lbQ().eq(UserRole::getRoleId, requiredRoleId));
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void deleteByUsers(Collection<Long> userIds) {
        List<Long> requiredUserIds = normalizedIds(userIds, "用户 ID");
        if (requiredUserIds.isEmpty()) {
            return;
        }
        lockUsers(requiredUserIds);
        baseMapper.delete(Wraps.<UserRole>lbQ().in(UserRole::getUserId, requiredUserIds));
    }

    /**
     * 所有关系写入统一按角色、用户 ID 升序加锁，避免跨入口死锁与陈旧覆盖。
     *
     * @param roleIds 需要锁定的角色 ID 集合
     */
    private void lockRoles(Collection<Long> roleIds) {
        List<Long> sorted = roleIds.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return;
        }
        roleMapper.selectList(Wraps.<Role>lbQ().in(Role::getId, sorted)
                .orderByAsc(Role::getId).last("FOR UPDATE"));
    }

    private void lockUsers(Collection<Long> userIds) {
        List<Long> sorted = userIds.stream().distinct().sorted().toList();
        if (sorted.isEmpty()) {
            return;
        }
        userMapper.selectList(Wraps.<User>lbQ().in(User::getId, sorted)
                .orderByAsc(User::getId).last("FOR UPDATE"));
    }

    private List<Long> normalizedIds(Collection<Long> ids, String field) {
        if (ids == null) {
            throw CheckedException.badRequest(field + "集合不能为空");
        }
        return ids.stream().map(id -> requireId(id, field)).distinct().sorted().toList();
    }

    private Long requireId(Long id, String field) {
        if (id == null || id <= 0) {
            throw CheckedException.badRequest(field + "必须为正整数");
        }
        return id;
    }

    private boolean sameIds(Collection<Long> first, Collection<Long> second) {
        return new LinkedHashSet<>(first).equals(new LinkedHashSet<>(second));
    }
}
