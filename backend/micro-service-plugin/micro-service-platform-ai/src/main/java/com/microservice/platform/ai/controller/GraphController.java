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

package com.microservice.platform.ai.controller;

import com.microservice.framework.commons.annotation.log.AccessLog;
import com.microservice.platform.ai.domain.dto.resp.EntityRecallResp;
import com.microservice.platform.ai.domain.dto.resp.GraphVisualizationResp;
import com.microservice.platform.ai.domain.dto.resp.GraphResult;
import com.microservice.platform.ai.domain.dto.resp.GraphStatisticsResp;
import com.microservice.platform.ai.service.GraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图谱化控制器
 * 提供对知识库内容进行图谱化的接口
 *
 * @author xJh
 * @since 2025/12/18
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "图谱化管理")
@RequestMapping("/graph")
public class GraphController {

    private final GraphService graphService;

    @PostMapping("/knowledge-item/{itemId}")
    @AccessLog(module = "图谱化管理", description = "图谱化知识条目")
    @Operation(summary = "对知识条目进行图谱化")
    public GraphResult graphizeKnowledgeItem(@PathVariable Long itemId) {
        return graphService.graphizeKnowledgeItem(itemId);
    }

    @PostMapping("/knowledge-items")
    @AccessLog(module = "图谱化管理", description = "批量图谱化知识条目")
    @Operation(summary = "批量对知识条目进行图谱化")
    public List<GraphResult> graphizeKnowledgeItems(@RequestBody List<Long> itemIds) {
        return graphService.graphizeKnowledgeItems(itemIds);
    }

    @DeleteMapping("/knowledge-item/{itemId}")
    @AccessLog(module = "图谱化管理", description = "删除知识条目图谱")
    @Operation(summary = "删除知识条目的图谱数据")
    public void deleteGraphForItem(@PathVariable Long itemId) {
        graphService.deleteGraphForItem(itemId);
    }

    @DeleteMapping("/knowledge-base/{kbId}")
    @AccessLog(module = "图谱化管理", description = "删除知识库图谱")
    @Operation(summary = "删除知识库的所有图谱数据")
    public void deleteGraphForKnowledgeBase(@PathVariable Long kbId) {
        graphService.deleteGraphForKnowledgeBase(kbId);
    }

    @GetMapping("/knowledge-base/{kbId}/statistics")
    @Operation(summary = "获取知识库的图谱统计信息")
    public GraphStatisticsResp getGraphStatistics(@PathVariable Long kbId) {
        return graphService.getGraphStatistics(kbId);
    }

    @GetMapping("/knowledge-base/{kbId}/schema")
    @Operation(summary = "获取知识库的图谱 Schema")
    public String getGraphSchema(@PathVariable Long kbId) {
        return graphService.getGraphSchema(kbId);
    }

    @GetMapping("/health")
    @Operation(summary = "检查图谱服务健康状态")
    public boolean checkHealth() {
        return graphService.isGraphServiceAvailable();
    }

    @GetMapping("/knowledge-base/{kbId}/visualization")
    @Operation(summary = "获取图谱可视化数据", description = "返回节点和关系数据，用于前端图谱可视化展示")
    public GraphVisualizationResp getVisualizationData(
                                                       @PathVariable Long kbId,
                                                       @RequestParam(defaultValue = "100") int limit) {
        return graphService.getVisualizationData(kbId, limit);
    }

    @PostMapping("/knowledge-base/{kbId}/recall-test")
    @Operation(summary = "实体召回测试", description = "根据关键词测试实体召回效果")
    public EntityRecallResp testEntityRecall(
                                             @PathVariable Long kbId,
                                             @RequestBody List<String> keywords) {
        return graphService.testEntityRecall(kbId, keywords);
    }
}
