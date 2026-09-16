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

package com.microservice.platform.ai.listener;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * @author xJh
 * @since 2025/10/16
 **/
@Slf4j
@Component
public class CustomizeChatModelListener implements ChatModelListener {

    @Override
    public void onRequest(final ChatModelRequestContext requestContext) {
        final var chatRequest = requestContext.chatRequest();
        var messages = chatRequest.messages();
        log.debug("onRequest: {}", messages);
    }

    @Override
    public void onResponse(final ChatModelResponseContext responseContext) {
        final var chatResponse = responseContext.chatResponse();
        var aiMessage = chatResponse.aiMessage();
        log.debug("onResponse: {}", aiMessage);
    }

    @Override
    public void onError(final ChatModelErrorContext errorContext) {
        ChatModelListener.super.onError(errorContext);
    }

}
