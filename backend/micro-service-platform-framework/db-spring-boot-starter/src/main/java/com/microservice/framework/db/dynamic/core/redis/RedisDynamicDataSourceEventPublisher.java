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

package com.microservice.framework.db.dynamic.core.redis;

import com.microservice.framework.commons.JacksonUtils;
import com.microservice.framework.db.dynamic.core.DynamicDataSourceEvent;
import com.microservice.framework.db.dynamic.core.DynamicDataSourceEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * @author Levin
 */
@Slf4j
public record RedisDynamicDataSourceEventPublisher(StringRedisTemplate redisTemplate) implements DynamicDataSourceEventPublisher {

    @Override
    public void publish(DynamicDataSourceEvent message) {
        String body = JacksonUtils.toJson(message);
        log.info("redis publish topic={}, message={}", DEFAULT_EVENT_TOPIC, body);
        redisTemplate.convertAndSend(DEFAULT_EVENT_TOPIC, body);
    }

}
