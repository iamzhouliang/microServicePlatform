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

package com.microservice.platform.ai.repository.workflow;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowTemplate;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI工作流模板 Mapper
 *
 * @author xJh
 * @since 2026/01/06
 */
@Repository
public interface WorkflowTemplateMapper extends SuperMapper<WorkflowTemplate> {

    /**
     * 查询内置模板列表
     *
     * @return 内置模板列表
     */
    @Select("SELECT * FROM ai_workflow_template WHERE built_in = 1 AND deleted = 0")
    List<WorkflowTemplate> selectBuiltInTemplates();

    /**
     * 按分类查询模板列表
     *
     * @param category 分类
     * @return 模板列表
     */
    @Select("SELECT * FROM ai_workflow_template WHERE category = #{category} AND deleted = 0")
    List<WorkflowTemplate> selectByCategory(@Param("category") String category);
}
