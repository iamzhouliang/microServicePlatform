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

import com.microservice.platform.ai.domain.dto.resp.EntityRecallResp;
import com.microservice.platform.ai.domain.dto.resp.GraphResult;
import com.microservice.platform.ai.domain.dto.resp.GraphStatisticsResp;
import com.microservice.platform.ai.domain.dto.resp.GraphVisualizationResp;

import java.util.List;

/**
 * 图谱化服务接口
 * 提供知识条目的图谱化处理功能
 *
 * @author xJh
 * @since 2025/12/18
 */
public interface GraphService {

    /**
     * 对知识条目进行图谱化
     *
     * @param itemId 知识条目ID
     * @return 图谱化结果（节点数、关系数）
     */
    GraphResult graphizeKnowledgeItem(Long itemId);

    /**
     * 批量对知识条目进行图谱化
     *
     * @param itemIds 知识条目ID列表
     * @return 批量处理结果
     */
    List<GraphResult> graphizeKnowledgeItems(List<Long> itemIds);

    /**
     * 删除知识条目的图谱数据（失败仅记录日志，走全局响应包装）
     *
     * @param itemId 知识条目ID
     */
    void deleteGraphForItem(Long itemId);

    /**
     * 删除知识库的所有图谱数据（失败仅记录日志，走全局响应包装）
     *
     * @param kbId 知识库ID
     */
    void deleteGraphForKnowledgeBase(Long kbId);

    /**
     * 获取知识库的图谱统计信息
     *
     * @param kbId 知识库ID
     * @return 统计信息
     */
    GraphStatisticsResp getGraphStatistics(Long kbId);

    /**
     * 获取知识库的图谱 Schema
     *
     * @param kbId 知识库ID
     * @return Schema 描述
     */
    String getGraphSchema(Long kbId);

    /**
     * 检查图谱服务是否可用
     *
     * @return 是否可用
     */
    boolean isGraphServiceAvailable();

    /**
     * 获取图谱可视化数据
     *
     * @param kbId  知识库ID
     * @param limit 最大节点数量
     * @return 可视化数据
     */
    GraphVisualizationResp getVisualizationData(Long kbId, int limit);

    /**
     * 实体召回测试
     *
     * @param kbId     知识库ID
     * @param keywords 关键词列表
     * @return 召回结果
     */
    EntityRecallResp testEntityRecall(Long kbId, List<String> keywords);
}
