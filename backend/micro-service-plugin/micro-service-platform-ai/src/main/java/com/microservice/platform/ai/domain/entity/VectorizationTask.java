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

package com.microservice.platform.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.platform.ai.core.enums.VectorizationTaskStatus;
import com.microservice.platform.ai.core.enums.VectorizationTaskType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 向量化任务
 * 记录知识条目的向量化处理状态和结果
 *
 * @author xJh
 * @since 2025/10/20
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ai_kb_vectorization_task", autoResultMap = true)
@Schema(description = "向量化任务")
public class VectorizationTask extends SuperEntity<Long> {

    @Schema(description = "任务ID（业务唯一标识）")
    private String taskId;

    @Schema(description = "知识库ID")
    private Long kbId;

    @Schema(description = "知识条目ID")
    private Long itemId;

    @Schema(description = "任务类型")
    private VectorizationTaskType taskType;

    @Schema(description = "任务状态")
    private VectorizationTaskStatus status;

    @Schema(description = "处理进度（0-100）")
    private Integer progress;

    @Schema(description = "结果向量ID列表")
    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> vectorIds;

    @Schema(description = "错误信息")
    private String errorMessage;

    @Schema(description = "消耗的Token数")
    private Integer tokenUsage;

    @Schema(description = "租户ID")
    private Long tenantId;

    /**
     * 是否已完成向量化
     * @return 处理结果
     */
    public boolean isVectorized() {
        return status == VectorizationTaskStatus.COMPLETED;
    }

    /**
     * 是否失败
     * @return 处理结果
     */
    public boolean isFailed() {
        return status == VectorizationTaskStatus.FAILED;
    }

    /**
     * 是否处理中
     * @return 处理结果
     */
    public boolean isProcessing() {
        return status == VectorizationTaskStatus.PROCESSING;
    }
}
