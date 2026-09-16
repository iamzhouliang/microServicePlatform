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

/**
 * 图谱化处理结果
 * 由 {@code GraphService} 的内部 record 提取为独立响应 DTO，避免用 Service 内部类型作对外契约。
 * JSON 字段（itemId/nodesCreated/relationshipsCreated/success/message）保持不变，前端零改动。
 *
 * @param itemId               知识条目ID
 * @param nodesCreated         创建的节点数
 * @param relationshipsCreated 创建的关系数
 * @param success              是否成功
 * @param message              失败信息（成功时为 null）
 */
public record GraphResult(
        Long itemId, int nodesCreated, int relationshipsCreated, boolean success, String message) {

    public static GraphResult success(Long itemId, int nodes, int relationships) {
        return new GraphResult(itemId, nodes, relationships, true, null);
    }

    public static GraphResult fail(Long itemId, String message) {
        return new GraphResult(itemId, 0, 0, false, message);
    }
}
