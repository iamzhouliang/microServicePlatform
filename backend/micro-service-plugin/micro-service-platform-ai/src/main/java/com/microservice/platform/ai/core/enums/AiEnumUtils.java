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

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 枚举解析工具。
 */
public final class AiEnumUtils {

    private AiEnumUtils() {
    }

    public static <E extends Enum<E> & AiEnum> Map<String, E> codeMap(Class<E> enumType) {
        return Arrays.stream(enumType.getEnumConstants())
                .collect(Collectors.toUnmodifiableMap(AiEnum::getCode, Function.identity()));
    }

    public static <E extends Enum<E> & AiEnum> E fromCode(Class<E> enumType, String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return codeMap(enumType).get(code);
    }

    public static <E extends Enum<E> & AiEnum> E requireCode(Class<E> enumType, String code) {
        E value = fromCode(enumType, code);
        if (value == null) {
            throw new IllegalArgumentException("未知枚举编码: " + enumType.getSimpleName() + "." + code);
        }
        return value;
    }
}
