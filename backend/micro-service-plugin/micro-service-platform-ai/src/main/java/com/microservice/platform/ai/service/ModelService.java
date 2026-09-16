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

package com.microservice.platform.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.dto.req.ModelPageReq;
import com.microservice.platform.ai.domain.dto.req.ModelSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ModelDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ModelPageResp;
import com.microservice.platform.ai.domain.entity.ModelEntity;

/**
 * 模型配置服务
 *
 * @author xJh
 * @since 2025/10/11
 **/
public interface ModelService extends SuperService<ModelEntity> {

    IPage<ModelPageResp> pageList(ModelPageReq req);

    ModelDetailResp detail(Long id);

    void create(ModelSaveReq req);

    void modify(Long id, ModelSaveReq req);

    void remove(Long id);
}
