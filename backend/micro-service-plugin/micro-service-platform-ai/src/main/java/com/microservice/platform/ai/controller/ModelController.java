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

package com.microservice.platform.ai.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.domain.dto.req.ModelPageReq;
import com.microservice.platform.ai.domain.dto.req.ModelSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ModelDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ModelPageResp;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.ModelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * AI 模型配置控制器
 * 管理 AI 模型配置，支持 OpenAI、通义千问、百度文心等模型
 *
 * @author Levin
 * @since 2025-10
 */
@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/models")
@Tag(name = "模型配置", description = "AI 模型配置管理")
public class ModelController {

    private final ModelService modelService;

    @GetMapping("/list")
    @Operation(summary = "查询模型列表", description = "按类型查询模型配置列表（不分页）")
    public List<ModelPageResp> list(String type) {
        // 归一化模型类型（大小写不敏感），避免大小写敏感库（如 PostgreSQL）按原串查不到；
        // type 显式传非法值 → 返回空；未传 → 查全部；合法值 → 按枚举精确查询
        ModelType modelType = ModelType.of(type);
        if (type != null && !type.isBlank() && modelType == null) {
            return List.of();
        }
        var list = modelService.list(Wraps.<ModelEntity>lbQ().eq(modelType != null, ModelEntity::getType, modelType));
        return BeanUtilPlus.toBeans(list, ModelPageResp.class);
    }

    @PostMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询模型配置列表")
    public IPage<ModelPageResp> page(@RequestBody ModelPageReq req) {
        return modelService.pageList(req);
    }

    @GetMapping("/{id}/detail")
    @Operation(summary = "配置详情", description = "获取模型配置详情")
    public ModelDetailResp detail(@PathVariable Long id) {
        return modelService.detail(id);
    }

    @PostMapping("/create")
    @AccessLog(module = "AI模型配置", description = "新增模型配置")
    @Operation(summary = "新增配置", description = "新增 AI 模型配置")
    public void create(@Validated @RequestBody ModelSaveReq req) {
        modelService.create(req);
    }

    @PutMapping("/{id}/modify")
    @AccessLog(module = "AI模型配置", description = "修改模型配置")
    @Operation(summary = "修改配置", description = "修改 AI 模型配置")
    public void update(@PathVariable Long id, @Validated @RequestBody ModelSaveReq req) {
        modelService.modify(id, req);
    }

    @DeleteMapping("/{id}")
    @AccessLog(module = "AI模型配置", description = "删除模型配置")
    @Operation(summary = "删除配置", description = "删除 AI 模型配置")
    public void delete(@PathVariable Long id) {
        modelService.remove(id);
    }
}
