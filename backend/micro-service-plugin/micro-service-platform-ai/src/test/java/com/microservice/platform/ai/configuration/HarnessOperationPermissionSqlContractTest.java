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

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * Harness Operation 控制面权限 SQL 契约测试。
 *
 * @author xJh
 * @since 2026-07-18
 */
class HarnessOperationPermissionSqlContractTest {

    @Test
    void mysqlMenuScriptShouldDeclareAuditApprovalAndReconcilePermissions() throws Exception {
        Path repository = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        while (repository != null && !Files.exists(repository.resolve("附件/mysql/micro-service-platform-ai-chat-menu.sql"))) {
            repository = repository.getParent();
        }
        assertThat(repository).as("必须能定位仓库根目录").isNotNull();
        String sql = Files.readString(repository.resolve("附件/mysql/micro-service-platform-ai-chat-menu.sql"),
                StandardCharsets.UTF_8);

        assertThat(sql).contains("ai:agent-operation:view", "ai:agent-operation:approve",
                "ai:agent-operation:reconcile");
        assertThat(sql).contains("role_id IN (1, 2)");
    }
}
