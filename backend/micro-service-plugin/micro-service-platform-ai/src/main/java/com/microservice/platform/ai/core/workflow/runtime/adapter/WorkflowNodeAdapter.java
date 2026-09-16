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

package com.microservice.platform.ai.core.workflow.runtime.adapter;

import com.microservice.platform.ai.core.workflow.runtime.WorkflowExecutionScope;
import com.microservice.platform.ai.core.workflow.runtime.CompiledWorkflow;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;

/**
 * 非确定性智能体节点的执行边界。
 *
 * @author xJh
 * @since 2026/05/24
 */
public interface WorkflowNodeAdapter {

    /**
     * 执行节点，并把输出写入智能体作用域。
     * @param executionContext executionContext 参数
     * @param node 工作流节点
     * @param scope scope 参数
     * @return 处理结果
     */
    Result execute(CompiledWorkflow.CompiledNode node, WorkflowExecutionScope scope, ExecutionContext executionContext);

    record Result(String selectedBranch, Boolean continueLoop) {

        public static Result completed() {
            return new Result(null, null);
        }

        public static Result branch(String selectedBranch) {
            return new Result(selectedBranch, null);
        }

        public static Result loop(boolean continueLoop) {
            return new Result(null, continueLoop);
        }
    }
}
