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

package com.microservice.framework.ai.harness.spill;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.microservice.framework.ai.harness.autoconfigure.HarnessAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class HarnessSpillProductionGuardTest {
    
    @Test
    void shouldRejectLocalFileSpillInProductionProfile() {
        HarnessSpillProperties properties = new HarnessSpillProperties();
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        
        assertThatThrownBy(() -> new HarnessAutoConfiguration().harnessSpillStore(properties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("生产环境", "对象存储");
    }
}
