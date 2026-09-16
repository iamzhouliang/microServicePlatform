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

package com.microservice.platform.ai.core.workflow.runtime;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 本地状态门面，对齐 LangChain4j WorkflowScope 的键值模型。
 *
 * @author xJh
 * @since 2026/05/24
 */
public class WorkflowExecutionScope {

    private final Map<String, Object> state = new LinkedHashMap<>();

    public void write(String key, Object value) {
        if (value == null) {
            state.remove(key);
            return;
        }
        state.put(key, value);
    }

    public void writeAll(Map<String, Object> values) {
        if (values == null) {
            return;
        }
        values.forEach(this::write);
    }

    public static WorkflowExecutionScope from(Map<String, Object> values) {
        WorkflowExecutionScope scope = new WorkflowExecutionScope();
        scope.writeAll(values);
        return scope;
    }

    public boolean has(String key) {
        return state.containsKey(key);
    }

    public Object read(String key) {
        return state.get(key);
    }

    public Map<String, Object> state() {
        return Map.copyOf(state);
    }
}
