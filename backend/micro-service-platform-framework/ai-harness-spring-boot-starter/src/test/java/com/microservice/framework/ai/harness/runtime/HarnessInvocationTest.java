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

package com.microservice.framework.ai.harness.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import org.junit.jupiter.api.Test;

class HarnessInvocationTest {
    
    @Test
    void newTurnShouldDeriveStableIndependentOperationForEachToolCall() {
        HarnessInvocation invocation = new HarnessInvocation("1", "2", "3", "turn-scope", "turn-1",
                Set.of("iam:user:create"), false);
        
        HarnessInvocation first = invocation.forToolCall("call-1");
        HarnessInvocation firstReplay = invocation.forToolCall("call-1");
        HarnessInvocation second = invocation.forToolCall("call-2");
        
        assertThat(first.operationId()).isEqualTo(firstReplay.operationId());
        assertThat(first.operationId()).isNotEqualTo(second.operationId());
    }
    
    @Test
    void resumedTurnShouldKeepPendingOperationId() {
        HarnessInvocation invocation = new HarnessInvocation("1", "2", "3", "pending-operation", "turn-2",
                Set.of("iam:user:create"), true);
        
        assertThat(invocation.forToolCall("new-call").operationId()).isEqualTo("pending-operation");
    }
}
