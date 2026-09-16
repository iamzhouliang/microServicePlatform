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

package com.microservice.platform.ai.core.workflow.config.node;

import com.microservice.platform.ai.core.enums.AiEnum;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 并行节点配置。
 *
 * @author xJh
 * @since 2026/05/24
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParallelNodeConfig {

    /**
     * 分支配置列表。
     */
    private List<ParallelBranch> branches;

    /**
     * 等待策略。
     */
    private WaitStrategy waitStrategy;

    /**
     * 超时时间，单位毫秒。
     */
    private Long timeout;

    /**
     * 并行分支定义。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ParallelBranch {

        /**
         * 分支 ID。
         */
        private String id;

        /**
         * 分支名称。
         */
        private String name;
    }

    /**
     * 并行等待策略。
     */
    @Getter
    @AllArgsConstructor
    public enum WaitStrategy implements AiEnum {

        ALL("ALL", "等待所有分支完成"),
        ANY("ANY", "任一分支完成即可"),
        FIRST("FIRST", "第一个分支完成即可");

        private final String code;
        private final String description;
    }
}
