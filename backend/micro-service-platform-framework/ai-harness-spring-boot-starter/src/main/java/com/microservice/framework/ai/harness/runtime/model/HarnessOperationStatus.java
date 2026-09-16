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

package com.microservice.framework.ai.harness.runtime.model;

/**
 * 受治理业务操作状态，不表达模型计划或步骤。
 *
 * @author xJh
 * @since 2026-07-18
 */
public enum HarnessOperationStatus {
    INTENT_RECORDED,
    WAITING_CONFIRMATION,
    WAITING_APPROVAL,
    EXECUTING,
    RESULT_UNKNOWN,
    SUCCEEDED,
    FAILED
}
