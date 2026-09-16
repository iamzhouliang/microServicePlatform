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

import com.microservice.platform.iam.feign.agent.domain.req.AgentRoleAssignmentReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserProvisionReq;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * IAM 智能体写操作请求摘要器。
 *
 * @author OmX
 */
final class IamAgentRequestDigest {
    
    private IamAgentRequestDigest() {
    }
    
    static String calculate(AgentUserCreateReq req) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "username", req.getUsername());
        append(canonical, "nickname", req.getNickname());
        append(canonical, "mobile", req.getMobile());
        append(canonical, "email", req.getEmail());
        append(canonical, "orgId", req.getOrgId() == null ? null : req.getOrgId().toString());
        return sha256(canonical);
    }
    
    static String calculate(AgentUserProvisionReq req) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "username", req.getUsername());
        append(canonical, "nickname", req.getNickname());
        append(canonical, "mobile", req.getMobile());
        append(canonical, "email", req.getEmail());
        append(canonical, "orgId", req.getOrgId() == null ? null : req.getOrgId().toString());
        String roleIds = req.getRoleIds() == null ? null
                : new TreeSet<>(req.getRoleIds()).stream().map(String::valueOf).collect(Collectors.joining(","));
        append(canonical, "roleIds", roleIds);
        return sha256(canonical);
    }
    
    /**
     * 角色集合按领域语义去重排序，避免仅列表顺序不同导致同一覆盖请求被误判为冲突。
     *
     * @param userId 用户 ID
     * @param req 角色分配请求
     * @return 稳定的 SHA-256 请求摘要
     */
    static String calculate(Long userId, AgentRoleAssignmentReq req) {
        StringBuilder canonical = new StringBuilder();
        append(canonical, "userId", userId == null ? null : userId.toString());
        String roleIds = req == null || req.getRoleIds() == null ? null
                : new TreeSet<>(req.getRoleIds()).stream().map(String::valueOf).collect(Collectors.joining(","));
        append(canonical, "roleIds", roleIds);
        String expectedCurrentRoleIds = req == null || req.getExpectedCurrentRoleIds() == null ? null
                : new TreeSet<>(req.getExpectedCurrentRoleIds()).stream()
                        .map(String::valueOf).collect(Collectors.joining(","));
        append(canonical, "expectedCurrentRoleIds", expectedCurrentRoleIds);
        return sha256(canonical);
    }
    
    private static String sha256(StringBuilder canonical) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256 请求摘要", exception);
        }
    }
    
    /**
     * 字段名固定排序，字段值使用长度前缀，避免 null、空串和分隔符产生摘要歧义。
     *
     * @param canonical 规范化摘要输入
     * @param field 字段名
     * @param value 字段值
     */
    private static void append(StringBuilder canonical, String field, String value) {
        canonical.append(field).append('=');
        if (value == null) {
            canonical.append("-1:");
        } else {
            canonical.append(value.length()).append(':').append(value);
        }
        canonical.append(';');
    }
}
