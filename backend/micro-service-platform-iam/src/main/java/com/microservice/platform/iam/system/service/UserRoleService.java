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

package com.microservice.platform.iam.system.service;

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.iam.system.domain.dto.resp.UserRoleResp;
import com.microservice.platform.iam.system.domain.entity.UserRole;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 业务接口
 * 角色分配
 * 账号角色绑定
 * </p>
 *
 * @author Levin
 * @since 2019-07-03
 */
public interface UserRoleService extends SuperService<UserRole> {

    /**
     * 根据劫色查询用户
     *
     * @param roleId 角色id
     * @return 查询结果
     */
    UserRoleResp findUserByRoleId(Long roleId);

    /**
     * 在用户资源锁保护下覆盖角色集合，并可校验写入前的期望状态。
     *
     * @param userId 用户 ID
     * @param roleIds 目标角色 ID 集合
     * @param expectedCurrentRoleIds 写入前预期角色 ID 集合
     * @return 写入前的角色 ID 快照
     */
    List<Long> replaceRolesForUser(Long userId, Collection<Long> roleIds,
                                   Collection<Long> expectedCurrentRoleIds);

    /**
     * 在角色和用户资源锁保护下覆盖角色的用户集合。
     *
     * @param roleId 角色 ID
     * @param userIds 目标用户 ID 集合
     */
    void replaceUsersForRole(Long roleId, Collection<Long> userIds);

    /**
     * 在角色和用户资源锁保护下删除角色关联。
     *
     * @param roleId 角色 ID
     */
    void deleteByRole(Long roleId);

    /**
     * 在用户资源锁保护下删除指定用户的全部角色关联。
     *
     * @param userIds 用户 ID 集合
     */
    void deleteByUsers(Collection<Long> userIds);

}
