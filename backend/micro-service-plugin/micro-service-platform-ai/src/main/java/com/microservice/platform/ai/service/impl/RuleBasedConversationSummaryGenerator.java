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

package com.microservice.platform.ai.service.impl;

import com.microservice.platform.ai.core.enums.MessageRole;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.service.ConversationSummaryGenerator;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 规则会话摘要生成器.
 *
 * @author xiao1
 * @since 2026-06
 */
@Component
public class RuleBasedConversationSummaryGenerator implements ConversationSummaryGenerator {

    private static final int MAX_SUMMARY_LENGTH = 4000;
    private static final List<String> SECTION_NAMES = List.of(
            "用户目标", "关键实体", "已确认事实", "已完成动作", "当前状态",
            "待处理事项", "约束偏好", "失败风险", "近期上下文");
    private static final Map<String, Integer> SECTION_LIMITS = Map.ofEntries(
            Map.entry("用户目标", 450),
            Map.entry("关键实体", 300),
            Map.entry("已确认事实", 650),
            Map.entry("已完成动作", 400),
            Map.entry("当前状态", 450),
            Map.entry("待处理事项", 300),
            Map.entry("约束偏好", 300),
            Map.entry("失败风险", 250),
            Map.entry("近期上下文", 650));

    @Override
    public String summarize(String previousSummary, List<ConversationTurn> newTurns) {
        Map<String, List<String>> sections = parsePrevious(previousSummary);
        List<ConversationTurn> turns = newTurns == null ? List.of() : newTurns;
        String latestUser = null;
        String latestAssistant = null;
        MessageRole latestRole = null;
        for (ConversationTurn turn : turns) {
            if (isInternalToolTurn(turn)) {
                continue;
            }
            String content = content(turn);
            if (StringUtils.isBlank(content)) {
                continue;
            }
            String normalized = content.trim();
            latestRole = turn.getRole();
            if (turn.getRole() == MessageRole.USER) {
                latestUser = normalized;
            } else if (turn.getRole() == MessageRole.ASSISTANT) {
                latestAssistant = normalized;
            }
            append(sections, "近期上下文", roleLabel(turn.getRole()) + ": "
                    + StringUtils.abbreviate(normalized, 240));
        }
        if (StringUtils.isNotBlank(latestUser)) {
            replace(sections, "用户目标", latestUser);
            append(sections, "关键实体", latestUser);
        }
        if (StringUtils.isNotBlank(latestAssistant)) {
            append(sections, "已完成动作", latestAssistant);
        }
        if (latestRole == MessageRole.USER && StringUtils.isNotBlank(latestUser)) {
            replace(sections, "当前状态", "等待继续处理：" + latestUser);
            replace(sections, "待处理事项", latestUser);
        } else if (StringUtils.isNotBlank(latestAssistant)) {
            replace(sections, "当前状态", latestAssistant);
            replace(sections, "待处理事项", "暂无明确待处理事项");
        }
        if (sections.get("失败风险").isEmpty()) {
            append(sections, "失败风险", "暂无新增失败风险");
        }
        String summary = render(sections);
        if (summary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalStateException("结构化会话摘要超过长度上限");
        }
        return summary;
    }

    private static boolean isInternalToolTurn(ConversationTurn turn) {
        if (turn == null || turn.getRole() == MessageRole.TOOL) {
            return true;
        }
        Object kind = turn.getVariables() == null ? null
                : turn.getVariables().get(ConversationMessageServiceImpl.MESSAGE_KIND);
        return ConversationMessageServiceImpl.AI_TOOL_REQUEST.equals(kind);
    }

    private Map<String, List<String>> parsePrevious(String previousSummary) {
        Map<String, List<String>> sections = emptySections();
        if (StringUtils.isBlank(previousSummary)) {
            return sections;
        }
        String activeSection = null;
        boolean structured = false;
        for (String line : previousSummary.strip().split("\\R")) {
            if (line.startsWith("## ") && sections.containsKey(line.substring(3).trim())) {
                activeSection = line.substring(3).trim();
                structured = true;
                continue;
            }
            if (activeSection != null && StringUtils.isNotBlank(line)) {
                append(sections, activeSection, line.replaceFirst("^-\\s*", "").trim());
            }
        }
        if (!structured) {
            append(sections, "已确认事实", previousSummary.trim());
            append(sections, "约束偏好", previousSummary.trim());
        }
        return sections;
    }

    private Map<String, List<String>> emptySections() {
        Map<String, List<String>> sections = new LinkedHashMap<>();
        SECTION_NAMES.forEach(name -> sections.put(name, new ArrayList<>()));
        return sections;
    }

    private void append(Map<String, List<String>> sections, String section, String value) {
        if (StringUtils.isNotBlank(value)) {
            sections.get(section).add(value.trim());
        }
    }

    private void replace(Map<String, List<String>> sections, String section, String value) {
        sections.get(section).clear();
        append(sections, section, value);
    }

    private String render(Map<String, List<String>> sections) {
        StringBuilder summary = new StringBuilder();
        for (String section : SECTION_NAMES) {
            if (!summary.isEmpty()) {
                summary.append('\n');
            }
            summary.append("## ").append(section).append('\n');
            String content = sections.get(section).isEmpty()
                    ? "暂无新增信息"
                    : String.join("\n- ", sections.get(section));
            summary.append("- ").append(StringUtils.abbreviate(content, SECTION_LIMITS.get(section))).append('\n');
        }
        return summary.toString().trim();
    }

    private String roleLabel(MessageRole role) {
        if (role == MessageRole.USER) {
            return "用户";
        }
        if (role == MessageRole.ASSISTANT) {
            return "助手";
        }
        return "消息";
    }

    private String content(ConversationTurn turn) {
        if (turn == null) {
            return null;
        }
        if (turn.getRole() == MessageRole.USER) {
            return firstNotBlank(turn.getUserInput(), turn.getDisplayContent(), turn.getModelOutput());
        }
        return firstNotBlank(turn.getModelOutput(), turn.getDisplayContent(), turn.getUserInput());
    }

    private String firstNotBlank(String... candidates) {
        for (String candidate : candidates) {
            if (StringUtils.isNotBlank(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
