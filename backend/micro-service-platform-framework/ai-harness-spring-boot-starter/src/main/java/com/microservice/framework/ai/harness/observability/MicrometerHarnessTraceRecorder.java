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

package com.microservice.framework.ai.harness.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;

/**
 * 将脱敏后的 Harness Trace 接入 Micrometer Observation。
 *
 * <p>部署侧配置 OpenTelemetry Observation Handler 后会自动导出 Trace；高基数字段不进入标签。</p>
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class MicrometerHarnessTraceRecorder implements HarnessTraceRecorder {
    
    private final ObservationRegistry registry;
    
    public MicrometerHarnessTraceRecorder(ObservationRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "ObservationRegistry 不能为空");
    }
    
    @Override
    public void record(HarnessTraceEvent event) {
        Observation observation = Observation.createNotStarted("micro-service.ai.harness", registry)
                .lowCardinalityKeyValue("harness.type", event.type())
                .lowCardinalityKeyValue("harness.status", event.status());
        observation.start();
        try {
            observation.event(Observation.Event.of("harness." + event.type().toLowerCase()));
        } finally {
            observation.stop();
        }
    }
}
