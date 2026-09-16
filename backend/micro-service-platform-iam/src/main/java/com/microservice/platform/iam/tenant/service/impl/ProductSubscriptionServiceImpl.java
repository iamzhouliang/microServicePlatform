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

package com.microservice.platform.iam.tenant.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.platform.iam.tenant.domain.dto.req.PlanSubscriptionSaveReq;
import com.microservice.platform.iam.tenant.domain.entity.PlanSubscription;
import com.microservice.platform.iam.tenant.repository.PlanSubscriptionMapper;
import com.microservice.platform.iam.tenant.service.ProductSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * @author Levin
 */
@Service
@RequiredArgsConstructor
public class ProductSubscriptionServiceImpl extends SuperServiceImpl<PlanSubscriptionMapper, PlanSubscription> implements ProductSubscriptionService {
    
    @Override
    public void create(PlanSubscriptionSaveReq req) {
        this.baseMapper.insert(BeanUtil.toBean(req, PlanSubscription.class));
    }
    
    @Override
    public void modify(Long id, PlanSubscriptionSaveReq req) {
        throw CheckedException.badRequest("禁止编辑");
    }
}
