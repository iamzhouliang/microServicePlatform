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
import com.microservice.platform.ai.domain.entity.workflow.WorkflowVersion;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI工作流版本历史 Mapper
 *
 * @author xJh
 * @since 2026/01/06
 */
@Repository
public interface WorkflowVersionMapper extends SuperMapper<WorkflowVersion> {

    /**
     * 获取工作流的版本历史列表
     *
     * @param workflowId 工作流ID
     * @return 版本历史列表
     */
    @Select("SELECT * FROM ai_workflow_version WHERE workflow_id = #{workflowId} ORDER BY version DESC")
    List<WorkflowVersion> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 获取工作流的最新版本号
     *
     * @param workflowId 工作流ID
     * @return 最新版本号
     */
    @Select("SELECT MAX(version) FROM ai_workflow_version WHERE workflow_id = #{workflowId}")
    Integer selectMaxVersion(@Param("workflowId") Long workflowId);

    /**
     * 获取指定版本的工作流
     *
     * @param workflowId 工作流ID
     * @param version    版本号
     * @return 版本信息
     */
    @Select("SELECT * FROM ai_workflow_version WHERE workflow_id = #{workflowId} AND version = #{version}")
    WorkflowVersion selectByWorkflowIdAndVersion(@Param("workflowId") Long workflowId, @Param("version") Integer version);
}
