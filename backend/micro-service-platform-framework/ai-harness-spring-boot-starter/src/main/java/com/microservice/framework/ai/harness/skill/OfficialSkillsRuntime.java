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

import dev.langchain4j.service.tool.ToolProvider;
import dev.langchain4j.service.tool.ToolProviderResult;
import dev.langchain4j.skills.Skills;

/**
 * 官方 Skills 与平台审计适配后的运行时视图。未发布任何 Skill 时提供空 ToolProvider，
 * 保证普通对话不依赖 Skill 配置即可使用。
 *
 * @param toolProvider 官方 Skills ToolProvider
 * @param availableSkills 模型可见的已发布 Skill 摘要
 * @author xJh
 * @since 2026-07-18
 */
public record OfficialSkillsRuntime(ToolProvider toolProvider, String availableSkills) {
    
    public static OfficialSkillsRuntime from(Skills skills, ToolProvider toolProvider) {
        return new OfficialSkillsRuntime(toolProvider, skills.formatAvailableSkills());
    }
    
    public static OfficialSkillsRuntime empty() {
        ToolProvider emptyProvider = request -> ToolProviderResult.builder().build();
        return new OfficialSkillsRuntime(emptyProvider, "");
    }

    public String formatAvailableSkills() {
        return availableSkills;
    }
}
