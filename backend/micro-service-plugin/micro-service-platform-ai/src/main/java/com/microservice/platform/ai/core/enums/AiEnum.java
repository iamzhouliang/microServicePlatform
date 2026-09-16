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

package com.microservice.platform.ai.core.enums;

/**
 * AI 模块公开枚举统一契约。
 * 所有会入库、出现在 API、被前端同步或用于诊断码的枚举都必须实现该接口。
 */
public interface AiEnum {

    /**
     * 稳定枚举编码。
     * @return 处理结果
     */
    default String getCode() {
        return ((Enum<?>) this).name();
    }

    /**
     * 中文展示名称。
     * @return 处理结果
     */
    default String getDescription() {
        return getCode();
    }
}
