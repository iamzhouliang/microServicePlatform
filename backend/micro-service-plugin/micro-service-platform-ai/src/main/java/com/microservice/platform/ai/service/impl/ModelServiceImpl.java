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

package com.microservice.platform.ai.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.exception.ModelNotFoundException;
import com.microservice.platform.ai.core.provider.scoring.ScoringModelService;
import com.microservice.platform.ai.core.provider.vector.VectorStoreFactory;
import com.microservice.platform.ai.domain.dto.req.ModelPageReq;
import com.microservice.platform.ai.domain.dto.req.ModelSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ModelDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ModelPageResp;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.repository.ModelMapper;
import com.microservice.platform.ai.service.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 模型配置服务实现
 *
 * @author xJh
 * @since 2025/10/11
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelServiceImpl extends SuperServiceImpl<ModelMapper, ModelEntity> implements ModelService {

    private final ScoringModelService scoringModelService;
    private final VectorStoreFactory vectorStoreFactory;

    @Override
    public IPage<ModelPageResp> pageList(ModelPageReq req) {
        log.debug("分页查询模型配置: provider={}, type={}, name={}",
                req.getProvider(), req.getType(), req.getName());

        return this.baseMapper.selectPage(req.buildPage(), Wraps.<ModelEntity>lbQ()
                .eq(ModelEntity::getProvider, req.getProvider())
                .eq(ModelEntity::getType, req.getType())
                .like(ModelEntity::getName, req.getName()))
                .convert(entity -> BeanUtil.toBean(entity, ModelPageResp.class));
    }

    @Override
    public ModelDetailResp detail(Long id) {
        log.debug("查询模型配置详情: id={}", id);

        ModelEntity modelEntity = Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> new ModelNotFoundException(id));

        return BeanUtil.toBean(modelEntity, ModelDetailResp.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(ModelSaveReq req) {
        log.info("创建模型配置: provider={}, type={}, name={}",
                req.getProvider(), req.getType(), req.getName());

        ModelEntity modelEntity = BeanUtil.toBean(req, ModelEntity.class);
        this.baseMapper.insert(modelEntity);

        log.info("模型配置创建成功: id={}", modelEntity.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, ModelSaveReq req) {
        log.info("修改模型配置: id={}", id);

        ModelEntity existingModel = Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> new ModelNotFoundException(id));

        ModelEntity modelEntity = BeanUtilPlus.toBean(id, req, ModelEntity.class);
        if (isMaskedApiKey(req.getApiKey())) {
            modelEntity.setApiKey(existingModel.getApiKey());
        }
        this.baseMapper.updateById(modelEntity);
        // 模型配置变更后失效相关缓存，避免继续命中旧配置（向量存储维度/连接、重排序模型实例）
        evictModelCaches();
        log.info("模型配置修改成功: id={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id) {
        log.info("删除模型配置: id={}", id);

        // 验证模型是否存在
        Optional.ofNullable(this.baseMapper.selectById(id))
                .orElseThrow(() -> new ModelNotFoundException(id));

        this.baseMapper.deleteById(id);
        // 模型删除后失效相关缓存，避免残留已删除模型的实例
        evictModelCaches();
        log.info("模型配置删除成功: id={}", id);
    }

    /**
     * 失效模型相关缓存：重排序模型实例缓存与知识库向量存储缓存。
     * 模型配置（维度 / 连接 / 密钥等）变更后必须清除，否则会继续命中旧实例。
     */
    private void evictModelCaches() {
        scoringModelService.clearCache();
        vectorStoreFactory.clearCache();
    }

    private static boolean isMaskedApiKey(String apiKey) {
        return apiKey != null && !apiKey.isBlank() && apiKey.chars().allMatch(ch -> ch == '*');
    }
}
