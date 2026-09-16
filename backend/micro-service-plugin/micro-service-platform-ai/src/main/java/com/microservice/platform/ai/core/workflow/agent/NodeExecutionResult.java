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

package com.microservice.platform.ai.core.workflow.agent;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 节点执行结果
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NodeExecutionResult {

    /**
     * 是否执行成功
     */
    private boolean success;

    /**
     * 输出数据
     */
    private Map<String, Object> outputs;

    /**
     * 错误信息（执行失败时）
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private Long duration;

    /**
     * 下一个要执行的分支（用于条件节点）
     */
    private String nextBranch;

    /**
     * 是否需要继续循环（用于循环节点）
     */
    private Boolean continueLoop;

    /**
     * 创建成功结果
     * @param outputs 输出数据
     * @return 处理结果
     */
    public static NodeExecutionResult success(Map<String, Object> outputs) {
        return NodeExecutionResult.builder()
                .success(true)
                .outputs(outputs)
                .build();
    }

    /**
     * 创建成功结果（带耗时）
     * @param duration 持续时间
     * @param outputs 输出数据
     * @return 处理结果
     */
    public static NodeExecutionResult success(Map<String, Object> outputs, long duration) {
        return NodeExecutionResult.builder()
                .success(true)
                .outputs(outputs)
                .duration(duration)
                .build();
    }

    /**
     * 创建失败结果
     * @param errorMessage 错误信息
     * @return 处理结果
     */
    public static NodeExecutionResult failure(String errorMessage) {
        return NodeExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
    }

    /**
     * 创建失败结果（带耗时）
     * @param duration 持续时间
     * @param errorMessage 错误信息
     * @return 处理结果
     */
    public static NodeExecutionResult failure(String errorMessage, long duration) {
        return NodeExecutionResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .duration(duration)
                .build();
    }

    /**
     * 创建条件分支结果
     * @param nextBranch nextBranch 参数
     * @return 处理结果
     */
    public static NodeExecutionResult branch(String nextBranch) {
        return NodeExecutionResult.builder()
                .success(true)
                .nextBranch(nextBranch)
                .build();
    }

    /**
     * 创建循环控制结果
     * @param continueLoop continueLoop 参数
     * @param outputs 输出数据
     * @return 处理结果
     */
    public static NodeExecutionResult loop(boolean continueLoop, Map<String, Object> outputs) {
        return NodeExecutionResult.builder()
                .success(true)
                .continueLoop(continueLoop)
                .outputs(outputs)
                .build();
    }
}
