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

import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.config.ConversationContextProperties;
import com.microservice.platform.ai.domain.entity.ConversationSummary;
import com.microservice.platform.ai.domain.entity.ConversationTurn;
import com.microservice.platform.ai.repository.ConversationSummaryMapper;
import com.microservice.platform.ai.service.ConversationMessageService;
import com.microservice.platform.ai.service.ConversationSummaryGenerator;
import com.microservice.platform.ai.service.ConversationSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 会话摘要服务实现.
 *
 * @author xiao1
 * @since 2026-06
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryServiceImpl extends SuperServiceImpl<ConversationSummaryMapper, ConversationSummary>
        implements
            ConversationSummaryService {

    private final ConversationMessageService conversationMessageService;
    private final ConversationSummaryGenerator summaryGenerator;
    private final ConversationContextProperties contextProperties;

    @Override
    public ConversationSummary getActiveSummary(Long conversationId) {
        if (conversationId == null) {
            return null;
        }
        ConversationSummary summary = getOne(Wraps.<ConversationSummary>lbQ()
                .eq(ConversationSummary::getConversationId, conversationId));
        if (summary == null || StringUtils.isBlank(summary.getSummaryContent())) {
            return null;
        }
        return summary;
    }

    @Override
    @Transactional(propagation = Propagation.NESTED)
    public void refreshSummaryIfNeeded(Long conversationId, Integer beforeSequenceNum) {
        ConversationContextProperties.Summary config = contextProperties.getSummary();
        if (!config.isEnabled() || conversationId == null || beforeSequenceNum == null) {
            return;
        }
        ConversationSummary summary = getOne(Wraps.<ConversationSummary>lbQ()
                .eq(ConversationSummary::getConversationId, conversationId));
        int coveredUntil = summary != null && summary.getCoveredUntilSequenceNum() != null
                ? summary.getCoveredUntilSequenceNum()
                : 0;
        int newMessageCount = beforeSequenceNum - coveredUntil - 1;
        if (newMessageCount < config.getMinMessages() && summary == null) {
            return;
        }
        if (newMessageCount < config.getIncrementalMessages()) {
            return;
        }

        List<ConversationTurn> newTurns = conversationMessageService.listTurnsBetween(conversationId, coveredUntil,
                beforeSequenceNum, config.getMaxSourceMessages());
        if (newTurns.isEmpty()) {
            return;
        }

        String previousSummary = summary == null ? null : summary.getSummaryContent();
        String summaryContent = summaryGenerator.summarize(previousSummary, newTurns);
        if (StringUtils.isBlank(summaryContent)) {
            log.warn("会话摘要生成结果为空, conversationId={}", conversationId);
            return;
        }

        Integer coveredUntilSequenceNum = newTurns.getLast().getSequenceNum();
        if (summary == null) {
            save(ConversationSummary.builder()
                    .conversationId(conversationId)
                    .summaryContent(summaryContent)
                    .coveredUntilSequenceNum(coveredUntilSequenceNum)
                    .version(1)
                    .deleted(false)
                    .build());
            return;
        }
        summary.setSummaryContent(summaryContent);
        summary.setCoveredUntilSequenceNum(coveredUntilSequenceNum);
        summary.setVersion(summary.getVersion() == null ? 1 : summary.getVersion() + 1);
        updateById(summary);
    }
}
