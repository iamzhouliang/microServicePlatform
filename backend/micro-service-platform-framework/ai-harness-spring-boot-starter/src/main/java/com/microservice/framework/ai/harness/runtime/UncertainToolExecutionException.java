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

/**
 * 表示外部调用已发出但无法确定是否产生副作用，禁止直接重试。
 *
 * @author xJh
 * @since 2026-07-18
 */
public class UncertainToolExecutionException extends RuntimeException {
    
    public UncertainToolExecutionException(String message) {
        super(message);
    }
    
    public UncertainToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
