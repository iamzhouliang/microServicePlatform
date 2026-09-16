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

import com.microservice.framework.ai.core.exception.AiException;
import lombok.Getter;

import java.io.Serial;

/**
 * AI 服务异常（业务层）
 * 继承自 ai-spring-boot-starter 的 AiException，支持错误码枚举
 * </p>
 *
 * @author Levin
 * @since 2025/12/27
 */
@Getter
public class AiServiceException extends AiException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 错误码枚举
     */
    private AiErrorCode errorCode;

    /**
     * 详细信息
     */
    private String detail;

    public AiServiceException(String message) {
        super(message);
    }

    public AiServiceException(String message, Throwable cause) {
        super(message, cause);
    }

    public AiServiceException(int code, String message) {
        super(code, message);
    }

    public AiServiceException(AiErrorCode errorCode) {
        super(errorCode.getValue(), errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public AiServiceException(AiErrorCode errorCode, String detail) {
        super(errorCode.getValue(), errorCode.getMessage() + ": " + detail);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public AiServiceException(AiErrorCode errorCode, Throwable cause) {
        super(errorCode.getMessage(), cause);
        this.errorCode = errorCode;
        this.detail = cause.getMessage();
    }

    public AiServiceException(AiErrorCode errorCode, String detail, Throwable cause) {
        super(errorCode.getMessage() + ": " + detail, cause);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    /**
     * 获取完整错误码
     * @return 处理结果
     */
    public String getFullCode() {
        return errorCode != null ? errorCode.getFullCode() : "AI_UNKNOWN";
    }

    // ==================== 快捷创建方法 ====================

    public static AiServiceException of(AiErrorCode errorCode) {
        return new AiServiceException(errorCode);
    }

    public static AiServiceException of(AiErrorCode errorCode, String detail) {
        return new AiServiceException(errorCode, detail);
    }

    public static AiServiceException of(AiErrorCode errorCode, Throwable cause) {
        return new AiServiceException(errorCode, cause);
    }

    public static AiServiceException modelNotFound(Long modelId) {
        return new AiServiceException(AiErrorCode.MODEL_NOT_FOUND, "modelId=" + modelId);
    }

    public static AiServiceException knowledgeBaseNotFound(Long kbId) {
        return new AiServiceException(AiErrorCode.KNOWLEDGE_BASE_NOT_FOUND, "kbId=" + kbId);
    }

    public static AiServiceException agentNotFound(Long agentId) {
        return new AiServiceException(AiErrorCode.AGENT_NOT_FOUND, "agentId=" + agentId);
    }

    public static AiServiceException vectorizationFailed(String reason) {
        return new AiServiceException(AiErrorCode.VECTORIZATION_FAILED, reason);
    }

    public static AiServiceException retrievalFailed(String reason) {
        return new AiServiceException(AiErrorCode.RETRIEVAL_FAILED, reason);
    }
}
