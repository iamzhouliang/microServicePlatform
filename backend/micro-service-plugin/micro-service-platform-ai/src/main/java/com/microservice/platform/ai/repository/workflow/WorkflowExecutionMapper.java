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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowExecution;
import org.springframework.stereotype.Repository;

/**
 * AI工作流执行记录 Mapper
 *
 * @author xJh
 * @since 2026/01/06
 */
@Repository
public interface WorkflowExecutionMapper extends SuperMapper<WorkflowExecution> {

    /**
     * 根据执行ID查询执行记录
     * 使用 MyBatis-Plus Wrapper 查询，确保 JSON 字段正确解析
     *
     * @param executionId 执行ID
     * @return 执行记录
     */
    default WorkflowExecution selectByExecutionId(String executionId) {
        return selectOne(new LambdaQueryWrapper<WorkflowExecution>()
                .eq(WorkflowExecution::getExecutionId, executionId));
    }
}
