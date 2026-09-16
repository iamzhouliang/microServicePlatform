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

package com.microservice.platform.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.ai.core.model.ModelConfig;
import com.microservice.framework.commons.entity.SuperEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * AI模型配置实体
 *
 * @author xJh
 * @since 2025/10/9
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "ai_model", autoResultMap = true)
@EqualsAndHashCode(callSuper = true)
@Schema(description = "AI模型配置")
public class ModelEntity extends SuperEntity<Long> implements ModelConfig {

    @Schema(description = "模型类型")
    private ModelType type;

    @Schema(description = "模型名称")
    private String name;

    @Schema(description = "模型提供商名称")
    private AiProvider provider;

    @Schema(description = "API密钥")
    private String apiKey;

    @Schema(description = "API基础URL")
    private String baseUrl;

    @Schema(description = "模型配置参数")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> variables;

    @Schema(description = "租户ID")
    private Long tenantId;

    @Builder.Default
    @TableField(exist = false)
    @Schema(description = "是否返回深度思考结果")
    private Boolean returnThinking = false;

    @Builder.Default
    @TableField(exist = false)
    @Schema(description = "是否启用联网搜索")
    private Boolean enableWebSearch = false;

    // ==================== ModelConfig 接口实现 ====================

    @Override
    public String getProviderCode() {
        return provider != null ? provider.getValue() : null;
    }

    @Override
    public String getModelType() {
        return type != null ? type.getValue() : null;
    }
}
