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

import com.microservice.platform.ai.domain.entity.ConversationTurn;

import java.util.List;

/**
 * 会话摘要生成器.
 *
 * @author xiao1
 * @since 2026-06
 */
public interface ConversationSummaryGenerator {

    /**
     * 根据旧摘要和新增消息生成新摘要.
     *
     * @param previousSummary 旧摘要
     * @param newTurns        新增消息
     * @return 新摘要
     */
    String summarize(String previousSummary, List<ConversationTurn> newTurns);
}
