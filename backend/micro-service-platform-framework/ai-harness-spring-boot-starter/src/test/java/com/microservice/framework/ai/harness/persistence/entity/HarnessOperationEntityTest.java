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

package com.microservice.framework.ai.harness.persistence.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Operation 可空状态字段持久化契约测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class HarnessOperationEntityTest {
    
    @Test
    void nullableStateFieldsShouldParticipateInUpdatesWhenCleared() throws NoSuchFieldException {
        List<String> nullableFields = List.of("leaseOwner", "leaseExpiresAt", "confirmedTurnId", "approvedBy",
                "resultText", "errorMessage");
        
        for (String fieldName : nullableFields) {
            TableField tableField = HarnessOperationEntity.class.getDeclaredField(fieldName)
                    .getAnnotation(TableField.class);
            assertThat(tableField)
                    .as("字段 %s 必须允许把旧值显式更新为 null", fieldName)
                    .isNotNull();
            assertThat(tableField.updateStrategy()).isEqualTo(FieldStrategy.ALWAYS);
        }
    }
}
