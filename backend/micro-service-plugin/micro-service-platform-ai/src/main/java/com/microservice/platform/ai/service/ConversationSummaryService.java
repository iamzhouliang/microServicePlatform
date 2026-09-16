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

package com.microservice.platform.ai.service;

import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.entity.ConversationSummary;

/**
 * 会话摘要服务.
 *
 * @author xiao1
 * @since 2026-06
 */
public interface ConversationSummaryService extends SuperService<ConversationSummary> {

    /**
     * 获取当前会话有效摘要.
     *
     * @param conversationId 会话ID
     * @return 当前摘要
     */
    ConversationSummary getActiveSummary(Long conversationId);

    /**
     * 按需刷新会话摘要.
     *
     * @param conversationId      会话ID
     * @param beforeSequenceNum   当前消息序号，摘要不覆盖该序号及之后的消息
     */
    void refreshSummaryIfNeeded(Long conversationId, Integer beforeSequenceNum);
}
