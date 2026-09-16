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

/**
 * Harness Trace 输出 SPI，可由业务系统适配 Micrometer 或 OpenTelemetry。
 *
 * @author xJh
 * @since 2026-07-18
 */
@FunctionalInterface
public interface HarnessTraceRecorder {
    
    void record(HarnessTraceEvent event);
    
    static HarnessTraceRecorder noop() {
        return event -> {
            // 显式空实现用于未配置外部可观测系统的场景。
        };
    }
}
