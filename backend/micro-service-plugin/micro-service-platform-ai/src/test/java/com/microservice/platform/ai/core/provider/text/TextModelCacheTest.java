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

package com.microservice.platform.ai.core.provider.text;

import com.microservice.framework.ai.core.enums.AiProvider;
import com.microservice.framework.ai.core.enums.ModelType;
import com.microservice.platform.ai.domain.entity.ModelEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("文本模型缓存")
class TextModelCacheTest {

    @Test
    @DisplayName("缓存注解使用可执行的统一缓存键生成器")
    void cacheAnnotationUsesExecutableKeyGenerator() throws Exception {
        ModelEntity config = model("secret-one", false, false);
        Method method = TextModelCache.class.getMethod("getStreamModel", ModelEntity.class);
        String expression = method.getAnnotation(Cacheable.class).key();
        StandardEvaluationContext context = new StandardEvaluationContext();
        context.setVariable("config", config);

        Object evaluated = new SpelExpressionParser().parseExpression(expression).getValue(context);

        assertThat(evaluated).isEqualTo(TextModelCache.generateCacheKey(config));
    }

    @Test
    @DisplayName("缓存键隔离动态能力且不泄露 API Key")
    void cacheKeySeparatesDynamicCapabilitiesWithoutLeakingSecrets() {
        ModelEntity base = model("secret-one", false, false);
        ModelEntity webSearch = model("secret-one", true, false);
        ModelEntity thinking = model("secret-one", false, true);
        ModelEntity otherCredential = model("secret-two", false, false);

        String baseKey = TextModelCache.generateCacheKey(base);

        assertThat(baseKey)
                .isNotEqualTo(TextModelCache.generateCacheKey(webSearch))
                .isNotEqualTo(TextModelCache.generateCacheKey(thinking))
                .isNotEqualTo(TextModelCache.generateCacheKey(otherCredential))
                .doesNotContain("secret-one", "secret-two");
    }

    private static ModelEntity model(String apiKey, boolean webSearch, boolean thinking) {
        return ModelEntity.builder()
                .provider(AiProvider.QWEN)
                .type(ModelType.TEXT)
                .name("qwen-max")
                .baseUrl("https://dashscope.aliyuncs.com")
                .apiKey(apiKey)
                .enableWebSearch(webSearch)
                .returnThinking(thinking)
                .build();
    }
}
