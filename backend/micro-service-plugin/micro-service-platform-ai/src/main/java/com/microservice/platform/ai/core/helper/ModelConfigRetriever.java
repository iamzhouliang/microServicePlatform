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

package com.microservice.platform.ai.core.helper;

import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.exception.ModelNotFoundException;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 模型配置获取器
 *
 * @author Levin
 * @since 2025-12-27
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ModelConfigRetriever {

    private final ModelService modelService;

    /**
     * 根据ID获取模型配置，不存在时抛出异常
     * @param modelId modelId 参数
     * @return 处理结果
     */
    public ModelEntity getRequiredModel(Long modelId) {
        return Optional.ofNullable(modelService.getById(modelId))
                .orElseThrow(() -> {
                    log.error("模型配置不存在: modelId={}", modelId);
                    return new ModelNotFoundException(modelId);
                });
    }

    /**
     * 根据ID和类型获取模型配置
     * @param expectedType expectedType 参数
     * @param modelId modelId 参数
     * @return 处理结果
     */
    public ModelEntity getRequiredModel(Long modelId, ModelType expectedType) {
        ModelEntity model = getRequiredModel(modelId);

        if (model.getType() != expectedType) {
            log.error("模型类型不匹配: modelId={}, expected={}, actual={}",
                    modelId, expectedType.getValue(), model.getType() != null ? model.getType().getValue() : null);
            throw CheckedException.badRequest(
                    String.format("模型类型不匹配，期望: %s, 实际: %s", expectedType.getValue(),
                            model.getType() != null ? model.getType().getValue() : null));
        }

        return model;
    }

    /**
     * 根据ID和类型获取模型配置（使用查询条件）
     * @param modelId modelId 参数
     * @param modelType modelType 参数
     * @return 处理结果
     */
    public ModelEntity getModelByIdAndType(Long modelId, ModelType modelType) {
        return Optional.ofNullable(
                modelService.getOne(
                        Wraps.<ModelEntity>lbQ()
                                .eq(ModelEntity::getId, modelId)
                                .eq(ModelEntity::getType, modelType)))
                .orElseThrow(() -> {
                    log.error("模型配置不存在或类型不匹配: modelId={}, type={}", modelId, modelType);
                    return new ModelNotFoundException(modelId);
                });
    }

    /**
     * 根据ID获取模型配置（可选）
     * @param modelId modelId 参数
     * @return 处理结果
     */
    public Optional<ModelEntity> getModel(Long modelId) {
        if (modelId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(modelService.getById(modelId));
    }
}
