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
 * 知识检索节点配置
 * 从知识库检索相关内容，支持元数据过滤
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeRetrievalConfig {

    /**
     * 知识库 ID 列表
     */
    private List<Long> knowledgeBaseIds;

    /**
     * 查询变量
     * 支持变量引用: {{nodeName.variableName}}
     */
    private String queryVariable;

    /**
     * 检索数量 (Top K)
     */
    private Integer topK;

    /**
     * 相似度阈值 (0-1)
     */
    private Double scoreThreshold;

    /**
     * 检索模式
     */
    private RetrievalMode retrievalMode;

    /**
     * 重排序配置
     */
    private RerankConfig rerankConfig;

    /**
     * 元数据过滤条件
     */
    private List<MetadataFilter> metadataFilters;

    /**
     * 输出变量名
     */
    private String outputVariable;

    /**
     * 是否返回元数据
     */
    private boolean includeMetadata;

    /**
     * 是否返回相似度分数
     */
    private boolean includeScore;

    /**
     * 检索模式枚举
     */
    @Getter
    @AllArgsConstructor
    public enum RetrievalMode implements AiEnum {

        /**
         * 向量检索
         * 基于语义相似度
         */
        VECTOR("VECTOR", "向量检索"),

        /**
         * 全文检索
         * 基于关键词匹配
         */
        FULLTEXT("FULLTEXT", "全文检索"),

        /**
         * 混合检索
         * 结合向量和全文检索
         */
        HYBRID("HYBRID", "混合检索"),
        /**
         * 混合检索
         * 结合向量和全文检索
         */
        KEYWORD("KEYWORD", "混合检索");

        private final String code;
        private final String description;
    }

    /**
     * 重排序配置
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RerankConfig {

        /**
         * 是否启用重排序
         */
        private boolean enabled;

        /**
         * 重排序模型 ID
         */
        private Long rerankModelId;

        /**
         * 重排序后保留的数量
         */
        private Integer topN;
    }

    /**
     * 元数据过滤条件
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetadataFilter {

        /**
         * 元数据字段名
         */
        private String field;

        /**
         * 过滤运算符
         */
        private FilterOperator operator;

        /**
         * 过滤值
         */
        private Object value;
    }

    /**
     * 过滤运算符枚举
     */
    @Getter
    @AllArgsConstructor
    public enum FilterOperator implements AiEnum {

        EQUALS("EQUALS", "等于"),
        NOT_EQUALS("NOT_EQUALS", "不等于"),
        CONTAINS("CONTAINS", "包含"),
        IN("IN", "在列表中"),
        NOT_IN("NOT_IN", "不在列表中"),
        GREATER_THAN("GREATER_THAN", "大于"),
        LESS_THAN("LESS_THAN", "小于"),
        GREATER_OR_EQUAL("GREATER_OR_EQUAL", "大于等于"),
        LESS_OR_EQUAL("LESS_OR_EQUAL", "小于等于");

        private final String code;
        private final String description;
    }
}
