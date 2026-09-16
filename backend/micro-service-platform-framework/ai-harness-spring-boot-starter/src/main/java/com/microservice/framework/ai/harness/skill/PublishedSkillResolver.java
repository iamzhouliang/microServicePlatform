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

import java.util.List;
import java.util.Optional;

/**
 * 业务模块向 Framework 提供已发布 Skill 精确修订的端口。
 *
 * @author xJh
 * @since 2026-07-18
 */
public interface PublishedSkillResolver {
    
    /**
     * 每个 code 最多返回一个当前激活修订。
     *
     * @return 当前可激活的 Skill 精确修订
     */
    List<PublishedSkill> resolveActive();
    
    /**
     * 重启恢复时按精确版本与摘要读取历史修订。
     *
     * @param code Skill 编码
     * @param version Skill 版本
     * @param digest 正文完整性摘要
     * @return 匹配的历史精确修订
     */
    Optional<PublishedSkill> resolveExact(String code, String version, String digest);
}
