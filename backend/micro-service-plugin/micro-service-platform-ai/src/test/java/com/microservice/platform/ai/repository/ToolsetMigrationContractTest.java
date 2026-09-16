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

package com.microservice.platform.ai.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/**
 * Toolset 数据库迁移与初始化脚本契约测试。
 *
 * @author xJh
 * @since 2026-07-20
 */
class ToolsetMigrationContractTest {

    @Test
    void freshFlywaySchemaShouldCreateNonNullToolsetAuditColumnAndIndex() throws IOException {
        String v9 = readModuleFile("src/main/resources/db/migration/"
                + "V9__native_harness_operation_and_skill_activation.sql").toLowerCase(Locale.ROOT);

        assertThat(v9)
                .contains("`toolset_id` varchar(255) not null")
                .contains("idx_harness_operation_toolset")
                .contains("(`tenant_id`, `toolset_id`, `last_modify_time`)");
    }

    @Test
    void mysqlUpgradeShouldCreateToolsetIndexIndependentlyFromColumnMigration() throws IOException {
        String v11 = readModuleFile("src/main/resources/db/migration/"
                + "V11__introduce_toolset_scope.sql").toLowerCase(Locale.ROOT);

        assertThat(v11)
                .contains("information_schema.statistics")
                .contains("index_name = 'idx_harness_operation_toolset'")
                .contains("create index `idx_harness_operation_toolset`");
    }

    @Test
    void canonicalMysqlSchemaShouldUseOnlyToolsetConfiguration() throws IOException {
        String schema = readRepositoryFile("附件/mysql/micro-service-platform-ai.sql").toLowerCase(Locale.ROOT);

        assertThat(schema)
                .contains("`toolset_ids` varchar(2000)")
                .contains("`requires_toolsets` varchar(2000)")
                .contains("`enabled_environments` varchar(500)")
                .contains("`required_permissions` varchar(1000)")
                .doesNotContain("`mcp_server_ids`")
                .doesNotContain("`tools` varchar");
    }

    @Test
    void manualHarnessSchemasShouldKeepToolsetIdentityNonNullAndIndexed() throws IOException {
        String mysql = readRepositoryFile("附件/mysql/micro-service-platform-ai-agent-harness.sql").toLowerCase(Locale.ROOT);
        String postgres = readRepositoryFile("附件/postgresql/micro-service-platform-ai-agent-harness.sql")
                .toLowerCase(Locale.ROOT);

        assertThat(mysql)
                .contains("`toolset_id` varchar(255) not null")
                .contains("idx_harness_operation_toolset");
        assertThat(postgres)
                .contains("toolset_id varchar(255) not null")
                .contains("idx_harness_operation_toolset");
    }

    @Test
    void postgresqlUpgradeShouldGuardRemovedLegacyColumns() throws IOException {
        String postgres = readRepositoryFile("附件/postgresql/micro-service-platform-ai-toolset.sql").toLowerCase(Locale.ROOT);

        assertThat(postgres)
                .contains("information_schema.columns")
                .contains("column_name = 'tools'")
                .contains("column_name = 'mcp_server_ids'")
                .contains("execute");
    }

    private static String readModuleFile(String relativePath) throws IOException {
        return Files.readString(moduleRoot().resolve(relativePath));
    }

    private static String readRepositoryFile(String relativePath) throws IOException {
        return Files.readString(moduleRoot().resolve("../..").normalize().resolve(relativePath));
    }

    private static Path moduleRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        return Files.exists(current.resolve("pom.xml")) ? current
                : current.resolve("micro-service-plugin/micro-service-platform-ai");
    }
}
