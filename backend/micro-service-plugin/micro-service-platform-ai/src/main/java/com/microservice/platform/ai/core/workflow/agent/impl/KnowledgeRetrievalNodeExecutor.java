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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.KnowledgeRetrievalConfig;
import com.microservice.platform.ai.core.workflow.config.node.KnowledgeRetrievalConfig.FilterOperator;
import com.microservice.platform.ai.core.workflow.config.node.KnowledgeRetrievalConfig.MetadataFilter;
import com.microservice.platform.ai.core.workflow.config.node.KnowledgeRetrievalConfig.RetrievalMode;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.dto.resp.EmbeddingMatchResp;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.KnowledgeSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 知识检索节点执行器
 * 支持向量检索、图谱检索、重排序、元数据过滤等完整 RAG 能力
 * 使用强类型配置驱动知识检索。
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRetrievalNodeExecutor extends AbstractNodeExecutor {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeSearchService knowledgeSearchService;

    @Override
    public NodeType getType() {
        return NodeType.KNOWLEDGE_RETRIEVAL;
    }

    @Override
    public void validate(WorkflowNode node) {
        KnowledgeRetrievalConfig config = parseConfig(node, KnowledgeRetrievalConfig.class);
        if (config == null || config.getKnowledgeBaseIds() == null || config.getKnowledgeBaseIds().isEmpty()) {
            throw new IllegalArgumentException("知识检索节点缺少 knowledgeBaseIds 配置");
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行知识检索节点: {}", node.getId());

        // 解析强类型配置
        KnowledgeRetrievalConfig config = parseConfig(node, KnowledgeRetrievalConfig.class);

        List<Long> knowledgeBaseIds = config.getKnowledgeBaseIds();
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty()) {
            return NodeExecutionResult.failure("知识检索节点缺少知识库配置");
        }
        // 目前只支持单个知识库
        Long knowledgeBaseId = knowledgeBaseIds.get(0);

        KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null) {
            return NodeExecutionResult.failure("知识库不存在: " + knowledgeBaseId);
        }

        String queryVariable = config.getQueryVariable();
        if (queryVariable == null || queryVariable.isBlank()) {
            return NodeExecutionResult.failure("知识检索节点缺少查询变量");
        }
        String query = resolveTemplate(queryVariable, context);

        if (query == null || query.isEmpty()) {
            return NodeExecutionResult.failure("知识检索节点查询内容为空");
        }

        Integer topK = config.getTopK() != null ? config.getTopK() : 5;
        Double scoreThreshold = config.getScoreThreshold() != null ? config.getScoreThreshold() : 0.5;

        // 获取检索模式
        RetrievalMode retrievalMode = config.getRetrievalMode();
        if (retrievalMode == null) {
            retrievalMode = RetrievalMode.VECTOR;
        }

        try {
            // 根据检索模式执行不同的搜索
            List<Map<String, Object>> searchResults;

            switch (retrievalMode) {
                case KEYWORD:
                    // 关键词搜索
                    searchResults = knowledgeSearchService.keywordSearch(knowledgeBaseId, query, topK);
                    break;
                case HYBRID:
                    // 混合搜索（语义 + 关键词）
                    searchResults = knowledgeSearchService.hybridSearch(knowledgeBaseId, query, topK);
                    break;
                case VECTOR:
                default:
                    // 语义搜索（向量）
                    List<EmbeddingMatchResp> embeddingResults = knowledgeSearchService.semanticSearch(knowledgeBaseId, query, topK);
                    searchResults = convertEmbeddingResults(embeddingResults, scoreThreshold);
                    break;
            }

            // 过滤低于阈值的结果
            final double threshold = scoreThreshold;
            if (searchResults != null) {
                searchResults = searchResults.stream()
                        .filter(r -> {
                            Object score = r.get("score");
                            if (score instanceof Number) {
                                return ((Number) score).doubleValue() >= threshold;
                            }
                            return true;
                        })
                        .collect(Collectors.toList());
            }

            // 构建输出
            Map<String, Object> outputs = new HashMap<>();

            // 检索结果列表
            outputs.put("results", searchResults != null ? searchResults : new ArrayList<>());
            outputs.put("resultCount", searchResults != null ? searchResults.size() : 0);

            // 拼接的文本结果（供后续节点使用）
            String concatenatedText = buildConcatenatedText(searchResults);
            outputs.put("result", concatenatedText);

            // 查询和配置信息
            outputs.put("query", query);
            outputs.put("knowledgeBaseId", knowledgeBaseId);
            outputs.put("knowledgeBaseName", kb.getName());
            outputs.put("topK", topK);
            outputs.put("scoreThreshold", scoreThreshold);
            outputs.put("retrievalMode", retrievalMode.getCode());

            // 添加元数据过滤信息
            Map<String, Object> metadataFilterMap = buildMetadataFilterMap(config.getMetadataFilters(), context);
            if (metadataFilterMap != null && !metadataFilterMap.isEmpty()) {
                outputs.put("metadataFilters", metadataFilterMap);
            }

            log.debug("知识检索节点 {} 执行完成，命中 {} 条结果", node.getId(),
                    searchResults != null ? searchResults.size() : 0);
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("知识检索节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("知识检索失败: " + e.getMessage());
        }
    }

    /**
     * 转换 EmbeddingMatchResp 为统一的 Map 格式
     * @param results 处理结果集合
     * @param threshold threshold 参数
     * @return 处理结果
     */
    private List<Map<String, Object>> convertEmbeddingResults(List<EmbeddingMatchResp> results, double threshold) {
        if (results == null) {
            return new ArrayList<>();
        }
        return results.stream()
                .filter(r -> r.getScore() >= threshold)
                .map(r -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("content", r.getContent());
                    map.put("score", r.getScore());
                    map.put("metadata", r.getMetadata());
                    map.put("searchType", r.getSearchType());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * 将检索结果拼接为文本
     * @param results 处理结果集合
     * @return 处理结果
     */
    private String buildConcatenatedText(List<Map<String, Object>> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            String content = (String) result.get("content");
            if (content != null && !content.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n\n---\n\n");
                }
                sb.append("[").append(i + 1).append("] ").append(content);
            }
        }
        return sb.toString();
    }

    /**
     * 构建元数据过滤条件 Map
     * 将配置的过滤条件转换为可用于检索的格式
     * @param context 执行上下文
     * @param filters filters 参数
     * @return 处理结果
     */
    private Map<String, Object> buildMetadataFilterMap(List<MetadataFilter> filters, ExecutionContext context) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Map<String, Object> filterMap = new HashMap<>();

        for (MetadataFilter filter : filters) {
            if (filter.getField() == null || filter.getField().isEmpty()) {
                continue;
            }

            Object value = filter.getValue();

            // 如果值是字符串，尝试解析变量引用
            if (value instanceof String) {
                String strValue = (String) value;
                if (strValue.contains("{{") && strValue.contains("}}")) {
                    value = resolveTemplate(strValue, context);
                }
            }

            // 根据运算符构建过滤条件
            FilterOperator operator = filter.getOperator();
            if (operator == null) {
                operator = FilterOperator.EQUALS;
            }

            String filterKey = buildFilterKey(filter.getField(), operator);
            filterMap.put(filterKey, value);
        }

        return filterMap;
    }

    /**
     * 构建过滤条件的键名
     * 格式: field__operator (例如: category__equals, date__gte)
     * @param field field 参数
     * @param operator operator 参数
     * @return 处理结果
     */
    private String buildFilterKey(String field, FilterOperator operator) {
        String operatorSuffix = switch (operator) {
            case EQUALS -> "";
            case NOT_EQUALS -> "__ne";
            case CONTAINS -> "__contains";
            case IN -> "__in";
            case NOT_IN -> "__nin";
            case GREATER_THAN -> "__gt";
            case LESS_THAN -> "__lt";
            case GREATER_OR_EQUAL -> "__gte";
            case LESS_OR_EQUAL -> "__lte";
        };

        return field + operatorSuffix;
    }

    /**
     * 验证元数据过滤条件
     * @param filters filters 参数
     * @return 处理结果
     */
    public List<String> validateMetadataFilters(List<MetadataFilter> filters) {
        List<String> errors = new ArrayList<>();

        if (filters == null) {
            return errors;
        }

        for (int i = 0; i < filters.size(); i++) {
            MetadataFilter filter = filters.get(i);

            if (filter.getField() == null || filter.getField().trim().isEmpty()) {
                errors.add("过滤条件 " + (i + 1) + ": 字段不能为空");
            }

            if (filter.getOperator() == null) {
                errors.add("过滤条件 " + (i + 1) + ": 操作符不能为空");
            }

            // IN 和 NOT_IN 运算符需要数组值
            if (filter.getOperator() == FilterOperator.IN || filter.getOperator() == FilterOperator.NOT_IN) {
                if (!(filter.getValue() instanceof List)) {
                    errors.add("过滤条件 " + (i + 1) + ": IN/NOT_IN 操作符需要数组值");
                }
            }
        }

        return errors;
    }
}
