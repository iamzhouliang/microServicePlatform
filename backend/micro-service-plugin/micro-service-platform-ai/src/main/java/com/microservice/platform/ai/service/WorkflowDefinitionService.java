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
import com.microservice.platform.ai.domain.dto.req.WorkflowPageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowDetailResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowPageResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowVersionResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowDefinition;

import java.util.List;

/**
 * 工作流定义服务接口
 * 提供工作流的 CRUD 操作、版本管理和发布功能
 *
 * @author xJh
 * @since 2026/01/07
 */
public interface WorkflowDefinitionService extends SuperService<WorkflowDefinition> {

    /**
     * 分页查询工作流
     *
     * @param req 分页查询请求
     * @return 工作流分页结果
     */
    IPage<WorkflowPageResp> pageList(WorkflowPageReq req);

    /**
     * 查询工作流详情
     *
     * @param id 工作流ID
     * @return 工作流详情
     */
    WorkflowDetailResp detail(Long id);

    /**
     * 创建工作流
     * 创建时自动创建版本1
     *
     * @param req 工作流创建请求
     * @return 工作流ID
     */
    Long create(WorkflowSaveReq req);

    /**
     * 更新工作流
     * 更新时自动创建新版本
     *
     * @param id  工作流ID
     * @param req 工作流更新请求
     */
    void modify(Long id, WorkflowSaveReq req);

    /**
     * 删除工作流
     *
     * @param id 工作流ID
     */
    void delete(Long id);

    /**
     * 发布工作流
     * 将当前版本标记为已发布状态
     *
     * @param id 工作流ID
     */
    void publish(Long id);

    /**
     * 归档工作流
     *
     * @param id 工作流ID
     */
    void archive(Long id);

    /**
     * 获取工作流版本历史
     *
     * @param id 工作流ID
     * @return 版本历史列表
     */
    List<WorkflowVersionResp> getVersionHistory(Long id);

    /**
     * 获取指定版本的工作流
     *
     * @param id      工作流ID
     * @param version 版本号
     * @return 版本详情
     */
    WorkflowVersionResp getVersion(Long id, Integer version);

    /**
     * 回滚到指定版本
     * 将指定版本的图定义设为当前版本
     *
     * @param id      工作流ID
     * @param version 目标版本号
     */
    void rollback(Long id, Integer version);

    /**
     * 从模板创建工作流
     *
     * @param templateId 模板ID
     * @param name       工作流名称
     * @param description 工作流描述
     * @return 工作流ID
     */
    Long createFromTemplate(Long templateId, String name, String description);

    /**
     * 复制工作流
     *
     * @param id   工作流ID
     * @param name 新工作流名称
     * @return 新工作流ID
     */
    Long copy(Long id, String name);
}
