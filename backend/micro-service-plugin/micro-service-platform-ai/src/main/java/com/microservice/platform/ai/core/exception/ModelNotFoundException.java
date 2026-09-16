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

package com.microservice.platform.ai.core.exception;

import com.microservice.framework.ai.core.constant.AiConstants;

import java.io.Serial;

/**
 * 模型配置不存在异常（业务层）
 *
 * @author Levin
 * @since 2025/12/27
 */
public class ModelNotFoundException extends AiServiceException {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final int NOT_FOUND = 404;

    public ModelNotFoundException(Long modelId) {
        super(NOT_FOUND, String.format(AiConstants.ERROR_MODEL_NOT_FOUND, modelId));
    }

    public ModelNotFoundException(String modelIdentifier) {
        super(NOT_FOUND, String.format(AiConstants.ERROR_MODEL_NOT_FOUND, modelIdentifier));
    }

    public ModelNotFoundException(Long modelId, Throwable cause) {
        super(String.format(AiConstants.ERROR_MODEL_NOT_FOUND, modelId), cause);
    }
}
