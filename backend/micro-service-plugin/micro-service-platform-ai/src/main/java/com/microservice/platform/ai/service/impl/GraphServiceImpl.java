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

package com.microservice.platform.ai.service.impl;

import com.microservice.platform.ai.core.provider.graph.GraphRagService;
import com.microservice.platform.ai.core.provider.graph.GraphRagTransformerFactory;
import com.microservice.platform.ai.core.provider.graph.GraphStore;
import com.microservice.platform.ai.core.provider.text.TextModelService;
import com.microservice.framework.commons.JacksonUtils;
import com.microservice.platform.ai.domain.dto.resp.EntityRecallResp;
import com.microservice.platform.ai.domain.dto.resp.GraphResult;
import com.microservice.platform.ai.domain.dto.resp.GraphStatisticsResp;
import com.microservice.platform.ai.domain.dto.resp.GraphVisualizationResp;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeChunk;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import com.microservice.platform.ai.service.*;
import dev.langchain4j.community.data.document.transformer.graph.LLMGraphTransformer;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 图谱化服务实现
 *
 * @author xJh
 * @since 2025/12/18
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphServiceImpl implements GraphService {

    private final KnowledgeItemService knowledgeItemService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeChunkService knowledgeChunkService;
    private final ModelService modelService;
    private final TextModelService textModelService;
    private final ApplicationContext applicationContext;

    @Autowired(required = false)
    private GraphRagService graphRagService;

    @Autowired(required = false)
    private GraphStore graphStore;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GraphResult graphizeKnowledgeItem(Long itemId) {
        if (!isGraphServiceAvailable()) {
            return GraphResult.fail(itemId, "图谱服务不可用");
        }

        KnowledgeItem item = knowledgeItemService.getById(itemId);
        if (item == null) {
            return GraphResult.fail(itemId, "知识条目不存在");
        }

        KnowledgeBase kb = knowledgeBaseService.getById(item.getKbId());
        if (kb == null) {
            return GraphResult.fail(itemId, "知识库不存在");
        }

        if (!Boolean.TRUE.equals(kb.getEnableGraph())) {
            return GraphResult.fail(itemId, "知识库未启用图谱功能");
        }

        try {
            // 获取聊天模型
            ChatModel chatModel = getChatModel(kb);
            if (chatModel == null) {
                return GraphResult.fail(itemId, "未配置聊天模型");
            }

            // 获取知识分片
            List<KnowledgeChunk> chunks = knowledgeChunkService.listByItemId(itemId);
            if (chunks.isEmpty()) {
                return GraphResult.fail(itemId, "知识条目没有分片数据");
            }

            // 转换为文档列表
            List<Document> documents = chunks.stream()
                    .map(chunk -> Document.from(chunk.getContent()))
                    .collect(Collectors.toList());

            // 创建图谱提取器
            LLMGraphTransformer graphTransformer = GraphRagTransformerFactory.create(chatModel);

            // 执行图谱提取和存储
            String graphKbId = String.valueOf(kb.getId());
            GraphRagService.ProcessResult result = graphRagService.processDocuments(
                    graphKbId, documents, graphTransformer, true);

            // 更新知识条目的图谱化状态
            item.setGraphed(true);
            knowledgeItemService.updateById(item);

            log.info("图谱化完成: itemId={}, nodes={}, relationships={}",
                    itemId, result.nodesCreated(), result.relationshipsCreated());

            return GraphResult.success(itemId, result.nodesCreated(), result.relationshipsCreated());

        } catch (Exception e) {
            log.error("图谱化失败: itemId={}", itemId, e);
            return GraphResult.fail(itemId, e.getMessage());
        }
    }

    @Override
    public List<GraphResult> graphizeKnowledgeItems(List<Long> itemIds) {
        List<GraphResult> results = new ArrayList<>();
        for (Long itemId : itemIds) {
            results.add(graphizeKnowledgeItem(itemId));
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGraphForItem(Long itemId) {
        if (!isGraphServiceAvailable()) {
            log.warn("图谱服务不可用，无法删除图谱数据: itemId={}", itemId);
            return;
        }

        try {
            KnowledgeItem item = knowledgeItemService.getById(itemId);
            if (item == null) {
                log.warn("删除图谱数据跳过，知识条目不存在: itemId={}", itemId);
                return;
            }

            // 删除图谱中该条目关联的文档节点
            String graphKbId = String.valueOf(item.getKbId());
            String documentId = "doc_" + item.getContent().hashCode();
            graphRagService.deleteDocument(graphKbId, documentId);

            // 更新状态
            item.setGraphed(false);
            knowledgeItemService.updateById(item);

            log.info("删除知识条目图谱数据: itemId={}", itemId);
        } catch (Exception e) {
            log.error("删除知识条目图谱数据失败: itemId={}", itemId, e);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteGraphForKnowledgeBase(Long kbId) {
        if (!isGraphServiceAvailable()) {
            log.warn("图谱服务不可用，无法删除图谱数据: kbId={}", kbId);
            return;
        }

        try {
            graphRagService.deleteKnowledgeBase(String.valueOf(kbId));

            // 批量更新知识条目状态
            // 注意：这里可以通过 SQL 批量更新，简化处理
            log.info("删除知识库图谱数据: kbId={}", kbId);
        } catch (Exception e) {
            log.error("删除知识库图谱数据失败: kbId={}", kbId, e);
        }
    }

    @Override
    public GraphStatisticsResp getGraphStatistics(Long kbId) {
        if (!isGraphServiceAvailable()) {
            return GraphStatisticsResp.unavailable("图谱服务不可用");
        }
        // 图谱存储实现返回动态 Map，这里在服务边界统一转为类型化 DTO，避免向上层/前端泄漏裸 Map
        Map<String, Object> stats = graphRagService.getStatistics(String.valueOf(kbId));
        return JacksonUtils.toBean(JacksonUtils.toJson(stats), GraphStatisticsResp.class);
    }

    @Override
    public String getGraphSchema(Long kbId) {
        if (!isGraphServiceAvailable()) {
            return "图谱服务不可用";
        }
        return graphRagService.getSchema(String.valueOf(kbId));
    }

    @Override
    public boolean isGraphServiceAvailable() {
        return graphRagService != null && graphStore != null && graphStore.isConnected();
    }

    @Override
    public GraphVisualizationResp getVisualizationData(Long kbId, int limit) {
        if (!isGraphServiceAvailable()) {
            return GraphVisualizationResp.builder()
                    .knowledgeBaseId(String.valueOf(kbId))
                    .nodes(List.of())
                    .edges(List.of())
                    .nodeCount(0)
                    .edgeCount(0)
                    .build();
        }

        GraphStore.GraphData graphData = graphStore.getGraphData(String.valueOf(kbId), limit);

        // 转换节点数据
        List<GraphVisualizationResp.GraphNode> nodes = graphData.nodes().stream()
                .map(n -> GraphVisualizationResp.GraphNode.builder()
                        .id((String) n.get("id"))
                        .name((String) n.get("name"))
                        .labels((List<String>) n.get("labels"))
                        .properties((Map<String, Object>) n.get("properties"))
                        .build())
                .toList();

        // 转换关系数据
        List<GraphVisualizationResp.GraphEdge> edges = graphData.edges().stream()
                .map(e -> GraphVisualizationResp.GraphEdge.builder()
                        .id((String) e.get("edgeId"))
                        .source((String) e.get("source"))
                        .target((String) e.get("target"))
                        .type((String) e.get("type"))
                        .label((String) e.get("label"))
                        .properties((Map<String, Object>) e.get("properties"))
                        .build())
                .toList();

        return GraphVisualizationResp.builder()
                .knowledgeBaseId(String.valueOf(kbId))
                .nodes(nodes)
                .edges(edges)
                .nodeCount(nodes.size())
                .edgeCount(edges.size())
                .build();
    }

    @Override
    public EntityRecallResp testEntityRecall(Long kbId, List<String> keywords) {
        if (!isGraphServiceAvailable()) {
            return EntityRecallResp.builder()
                    .knowledgeBaseId(String.valueOf(kbId))
                    .keywords(keywords)
                    .entities(List.of())
                    .triples(List.of())
                    .entityCount(0)
                    .tripleCount(0)
                    .build();
        }

        String graphKbId = String.valueOf(kbId);

        // 确保向量索引存在
        graphStore.ensureVectorIndex(graphKbId);

        // 执行实体召回
        var graphRetriever = getGraphRetriever();
        if (graphRetriever == null) {
            return EntityRecallResp.builder()
                    .knowledgeBaseId(graphKbId)
                    .keywords(keywords)
                    .entities(List.of())
                    .triples(List.of())
                    .entityCount(0)
                    .tripleCount(0)
                    .build();
        }

        // 使用实体精确匹配搜索（精确路）
        List<String> entityIds = graphRetriever.searchByEntityMatch(graphKbId, keywords, 20);

        // 构建实体信息
        List<EntityRecallResp.RecalledEntity> entities = entityIds.stream()
                .map(entityId -> EntityRecallResp.RecalledEntity.builder()
                        .id(entityId)
                        .name(entityId)
                        .build())
                .toList();

        // 扩展子图获取三元组
        List<String> triples = List.of();
        if (!entityIds.isEmpty()) {
            triples = graphRetriever.expandSubgraph(graphKbId, entityIds, 1, 30);
        }

        return EntityRecallResp.builder()
                .knowledgeBaseId(graphKbId)
                .keywords(keywords)
                .entities(entities)
                .triples(triples)
                .entityCount(entities.size())
                .tripleCount(triples.size())
                .build();
    }

    private com.microservice.platform.ai.core.provider.graph.GraphRetriever getGraphRetriever() {
        try {
            return applicationContext.getBean(com.microservice.platform.ai.core.provider.graph.GraphRetriever.class);
        } catch (Exception e) {
            log.warn("GraphRetriever 未注入");
            return null;
        }
    }

    private ChatModel getChatModel(KnowledgeBase kb) {
        Long chatModelId = kb.getChatModelId();
        if (chatModelId == null) {
            return null;
        }
        ModelEntity config = modelService.getById(chatModelId);
        if (config == null) {
            return null;
        }
        return textModelService.model(config);
    }
}
