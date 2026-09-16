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

package com.microservice.framework.ai.harness.skill;

import java.time.Instant;

/**
 * 官方 activate_skill 成功后的审计记录。
 *
 * @param tenantId 租户标识
 * @param userId 用户标识
 * @param conversationId 会话标识
 * @param turnId 激活所在用户轮标识
 * @param skillCode Skill 编码
 * @param skillVersion Skill 版本
 * @param contentDigest 正文完整性摘要
 * @param activatedAt 激活时间
 * @author xJh
 * @since 2026-07-18
 */
public record SkillActivation(String tenantId, String userId, String conversationId, String turnId,
        String skillCode, String skillVersion, String contentDigest, Instant activatedAt) {
}
