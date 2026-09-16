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

import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;

/**
 * 图谱提取服务接口
 * 负责从知识条目中提取实体关系并存储到知识图谱
 *
 * @author xJh
 * @since 2025/12/28
 */
public interface GraphExtractionService {

    /**
     * 处理图谱提取
     *
     * @param item 知识条目
     * @param kb   知识库
     * @return 提取结果
     */
    GraphExtractionResult extractAndStore(KnowledgeItem item, KnowledgeBase kb);

    /**
     * 异步处理图谱提取
     *
     * @param item 知识条目
     * @param kb   知识库
     */
    void extractAndStoreAsync(KnowledgeItem item, KnowledgeBase kb);

    /**
     * 删除知识条目的图谱数据
     *
     * @param itemId 知识条目ID
     * @param kbId   知识库ID
     * @return 是否删除成功
     */
    boolean deleteGraphData(Long itemId, Long kbId);

    /**
     * 图谱提取结果
     * @param errorMessage 错误信息
     * @param nodesCreated nodesCreated 参数
     * @param relationshipsCreated relationshipsCreated 参数
     * @param success success 参数
     */
    record GraphExtractionResult(
            int nodesCreated,
            int relationshipsCreated,
            boolean success,
            String errorMessage
    ) {
        public static GraphExtractionResult success(int nodes, int relationships) {
            return new GraphExtractionResult(nodes, relationships, true, null);
        }

        public static GraphExtractionResult failure(String errorMessage) {
            return new GraphExtractionResult(0, 0, false, errorMessage);
        }
    }
}
