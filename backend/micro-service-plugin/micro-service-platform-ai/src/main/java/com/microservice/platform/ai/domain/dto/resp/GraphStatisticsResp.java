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

package com.microservice.platform.ai.domain.dto.resp;

import java.util.List;

/**
 * 知识库图谱统计信息
 * 替代 {@code GraphController.getGraphStatistics} 直接返回的 {@code Map<String, Object>}。
 * 字段与图谱存储实现（Neo4j）输出的键一一对应，JSON 保持不变，前端零改动；
 * 图谱服务不可用时仅填充 {@link #error}（其余为 null）。
 *
 * @param nodeCount                    节点总数
 * @param documentCount                文档节点数
 * @param relationshipCount            关系总数
 * @param entityTypeDistribution       实体类型分布（Top N）
 * @param relationshipTypeDistribution 关系类型分布（Top N）
 * @param knowledgeBaseId              知识库ID
 * @param label                        图谱标签
 * @param error                        错误信息（服务不可用时）
 */
public record GraphStatisticsResp(
        Long nodeCount,
        Long documentCount,
        Long relationshipCount,
        List<EntityTypeCount> entityTypeDistribution,
        List<RelationshipTypeCount> relationshipTypeDistribution,
        String knowledgeBaseId,
        String label,
        String error) {

    public static GraphStatisticsResp unavailable(String message) {
        return new GraphStatisticsResp(null, null, null, null, null, null, null, message);
    }

    /**
     * 实体类型分布项
     *
     * @param types 节点标签集合
     * @param count 数量
     */
    public record EntityTypeCount(List<String> types, Long count) { }

    /**
     * 关系类型分布项
     *
     * @param type  关系类型
     * @param count 数量
     */
    public record RelationshipTypeCount(String type, Long count) { }
}
