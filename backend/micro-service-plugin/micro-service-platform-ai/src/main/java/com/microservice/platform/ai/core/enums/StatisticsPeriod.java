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

package com.microservice.platform.ai.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.microservice.framework.commons.entity.DictEnum;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统计周期
 *
 * @author xiao1
 * @since 2026-06
 */
@Getter
@AllArgsConstructor
public enum StatisticsPeriod implements DictEnum<String>, AiEnum {

    /**
     * 今日
     */
    DAY("DAY", "今日"),

    /**
     * 本周
     */
    WEEK("WEEK", "本周"),

    /**
     * 本月
     */
    MONTH("MONTH", "本月");

    @EnumValue
    @JsonValue
    private final String value;

    private final String label;

    @JsonCreator
    public static StatisticsPeriod of(String value) {
        if (value == null || value.isBlank()) {
            return DAY;
        }
        for (StatisticsPeriod period : values()) {
            if (period.value.equalsIgnoreCase(value)) {
                return period;
            }
        }
        return DAY;
    }

    @Override
    public String getCode() {
        return value;
    }

    @Override
    public String getDescription() {
        return label;
    }
}
