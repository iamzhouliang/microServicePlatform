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

package com.microservice.platform.ai.core.workflow;

import com.microservice.platform.ai.core.workflow.definition.WorkflowRuntimeEventType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 工作流运行事件契约测试。
 */
class WorkflowRuntimeEventContractTest {

    @Test
    void shouldExposeCanonicalRuntimeEventNames() {
        Set<String> codes = Arrays.stream(WorkflowRuntimeEventType.values())
                .map(WorkflowRuntimeEventType::code)
                .collect(Collectors.toSet());

        assertThat(codes).containsExactlyInAnyOrder(
                "workflow.started",
                "workflow.resumed",
                "node.started",
                "node.delta",
                "node.completed",
                "node.failed",
                "workflow.paused",
                "workflow.completed",
                "workflow.failed",
                "workflow.cancelled");
    }

    @Test
    void shouldRejectLegacyEventNames() {
        Set<String> codes = Arrays.stream(WorkflowRuntimeEventType.values())
                .map(WorkflowRuntimeEventType::code)
                .collect(Collectors.toSet());

        assertThat(codes).doesNotContain(
                "execution-started",
                "node-started",
                "node-completed",
                "node-error",
                "stream-token",
                "breakpoint-hit");
    }
}
