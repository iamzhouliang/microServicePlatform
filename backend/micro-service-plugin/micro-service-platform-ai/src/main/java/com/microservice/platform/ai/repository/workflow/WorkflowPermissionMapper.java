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
import com.microservice.platform.ai.domain.entity.workflow.WorkflowPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI工作流权限 Mapper
 *
 * @author xJh
 * @since 2026/01/06
 */
@Repository
public interface WorkflowPermissionMapper extends SuperMapper<WorkflowPermission> {

    /**
     * 查询用户对工作流的权限
     *
     * @param workflowId 工作流ID
     * @param userId     用户ID
     * @return 权限信息
     */
    @Select("SELECT * FROM ai_workflow_permission WHERE workflow_id = #{workflowId} AND user_id = #{userId}")
    WorkflowPermission selectByWorkflowAndUser(@Param("workflowId") Long workflowId, @Param("userId") Long userId);

    /**
     * 查询工作流的所有权限
     *
     * @param workflowId 工作流ID
     * @return 权限列表
     */
    @Select("SELECT * FROM ai_workflow_permission WHERE workflow_id = #{workflowId}")
    List<WorkflowPermission> selectByWorkflowId(@Param("workflowId") Long workflowId);

    /**
     * 删除工作流的所有权限
     *
     * @param workflowId 工作流ID
     * @return 删除数量
     */
    @Delete("DELETE FROM ai_workflow_permission WHERE workflow_id = #{workflowId}")
    int deleteByWorkflowId(@Param("workflowId") Long workflowId);
}
