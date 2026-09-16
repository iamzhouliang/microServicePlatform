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

package com.microservice.platform.ai.configuration;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.framework.db.mybatisplus.wrap.update.LbuWrapper;
import com.microservice.platform.ai.domain.entity.VectorizationTask;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Lambda update wrapper")
class LbuWrapperTest {

    @Test
    @DisplayName("resolves method references to database column names")
    void resolvesMethodReferencesToDatabaseColumnNames() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                VectorizationTask.class);
        LbuWrapper<VectorizationTask> wrapper = Wraps.<VectorizationTask>lbU()
                .set(VectorizationTask::getStatus, "PROCESSING")
                .set(VectorizationTask::getProgress, 10)
                .set(VectorizationTask::getErrorMessage, null);

        assertThat(wrapper.getSqlSet())
                .startsWith("status=")
                .contains(",progress=")
                .contains(",error_message=")
                .doesNotContain("$$Lambda");
    }
}
