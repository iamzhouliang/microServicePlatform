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
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplatePageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowTemplateSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowTemplateResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowTemplate;

import java.util.List;

/**
 * 工作流模板服务接口
 * 提供模板的 CRUD 操作和预定义模板管理
 *
 * @author xJh
 * @since 2026/01/07
 */
public interface WorkflowTemplateService extends SuperService<WorkflowTemplate> {

    /**
     * 分页查询模板
     *
     * @param req 分页查询请求
     * @return 模板分页结果
     */
    IPage<WorkflowTemplateResp> pageList(WorkflowTemplatePageReq req);

    /**
     * 查询模板详情
     *
     * @param id 模板ID
     * @return 模板详情
     */
    WorkflowTemplateResp detail(Long id);

    /**
     * 获取所有内置模板
     *
     * @return 内置模板列表
     */
    List<WorkflowTemplateResp> listBuiltIn();

    /**
     * 按分类获取模板
     *
     * @param category 分类
     * @return 模板列表
     */
    List<WorkflowTemplateResp> listByCategory(String category);

    /**
     * 创建自定义模板
     *
     * @param req 模板创建请求
     * @return 模板ID
     */
    Long create(WorkflowTemplateSaveReq req);

    /**
     * 从工作流创建模板
     *
     * @param workflowId  工作流ID
     * @param name        模板名称
     * @param description 模板描述
     * @param category    模板分类
     * @return 模板ID
     */
    Long createFromWorkflow(Long workflowId, String name, String description, String category);

    /**
     * 更新模板
     *
     * @param id  模板ID
     * @param req 模板更新请求
     */
    void modify(Long id, WorkflowTemplateSaveReq req);

    /**
     * 删除模板
     * 只能删除自定义模板，不能删除内置模板
     *
     * @param id 模板ID
     */
    void delete(Long id);

    /**
     * 导出模板为 JSON
     *
     * @param id 模板ID
     * @return JSON 字符串
     */
    String exportTemplate(Long id);

    /**
     * 从 JSON 导入模板
     *
     * @param json JSON 字符串
     * @return 模板ID
     */
    Long importTemplate(String json);

    /**
     * 初始化预定义模板
     * 在系统启动时调用，创建内置模板
     */
    void initBuiltInTemplates();
}
