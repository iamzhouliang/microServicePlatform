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

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.configuration.BaseMybatisConfiguration;
import com.microservice.framework.db.properties.DatabaseProperties;
import com.microservice.framework.db.properties.MultiTenantType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@DisplayName("MyBatis-Plus global interceptor configuration")
class BaseMybatisConfigurationTest {

    @Test
    @DisplayName("registers optimistic locking before pagination")
    void registersOptimisticLockingBeforePagination() {
        DatabaseProperties properties = new DatabaseProperties();
        properties.getMultiTenant().setType(MultiTenantType.NONE);
        properties.getDataPermission().setEnabled(false);
        properties.getIntercept().setBlockAttack(false);
        properties.getIntercept().getPagination().setDbType(DbType.MYSQL);

        BaseMybatisConfiguration configuration = new BaseMybatisConfiguration(
                properties, mock(AuthenticationContext.class)) {
        };

        MybatisPlusInterceptor interceptor = configuration.mybatisPlusInterceptor();

        assertThat(interceptor.getInterceptors())
                .extracting(Object::getClass)
                .containsExactly(
                        OptimisticLockerInnerInterceptor.class,
                        PaginationInnerInterceptor.class);
    }
}
