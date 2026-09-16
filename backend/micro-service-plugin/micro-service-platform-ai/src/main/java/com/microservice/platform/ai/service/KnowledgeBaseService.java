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
import com.microservice.platform.ai.domain.dto.req.KnowledgeBasePageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeBaseSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeBaseResp;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;

/**
 * 知识库服务接口
 *
 * @author xJh
 * @since 2025/10/20
 **/
public interface KnowledgeBaseService extends SuperService<KnowledgeBase> {

    /**
     * 分页查询知识库
     *
     * @param req 分页查询请求
     * @return 知识库分页结果
     */
    IPage<KnowledgeBaseResp> pageList(KnowledgeBasePageReq req);

    /**
     * 查询知识库列表（不分页）
     *
     * @return 知识库列表
     */
    java.util.List<KnowledgeBaseResp> listAll();

    /**
     * 查询知识库详情
     *
     * @param id 知识库ID
     * @return 知识库详情
     */
    KnowledgeBaseResp detail(Long id);

    /**
     * 创建知识库
     *
     * @param req 知识库创建请求
     */
    void create(KnowledgeBaseSaveReq req);

    /**
     * 更新知识库
     *
     * @param id 知识库ID
     * @param req 知识库更新请求
     */
    void modify(Long id, KnowledgeBaseSaveReq req);

    /**
     * 删除知识库
     *
     * @param id 知识库ID
     */
    void delete(Long id);
}
