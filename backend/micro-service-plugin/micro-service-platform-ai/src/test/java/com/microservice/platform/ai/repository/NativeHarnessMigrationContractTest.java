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
import org.junit.jupiter.api.Test;

class NativeHarnessMigrationContractTest {

    private static final String LEGACY_AGENT_RUN_TABLE = "CREATE TABLE IF NOT EXISTS `ai_agent_run`";
    private static final String LEGACY_AGENT_RUN_STEP_TABLE = "CREATE TABLE IF NOT EXISTS `ai_agent_run_step`";
    private static final String LEGACY_AGENT_APPROVAL_TABLE = "CREATE TABLE IF NOT EXISTS `ai_agent_approval`";
    private static final String LEGACY_AGENT_EVENT_TABLE = "CREATE TABLE IF NOT EXISTS `ai_agent_event`";
    private static final String LEGACY_AGENT_FEEDBACK_TABLE = "CREATE TABLE IF NOT EXISTS `ai_agent_feedback`";

    @Test
    void mysqlMigrationShouldContainOperationFencingAndSkillRevisionAudit() throws IOException {
        String sql = Files.readString(Path.of("src/main/resources/db/migration/"
                + "V9__native_harness_operation_and_skill_activation.sql"));

        assertThat(sql).contains("ai_harness_operation", "ai_skill_activation",
                "confirmation_required", "approval_required", "fencing_token", "version",
                "uk_harness_operation_tenant_operation", "uk_skill_activation_turn_code");
    }

    @Test
    void manualScriptsShouldCoverMysqlAndPostgresqlWithoutDroppingLegacyData() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize().getParent();
        String mysql = Files.readString(repository.resolve("附件/mysql/micro-service-platform-ai-agent-harness.sql"));
        String postgres = Files.readString(repository.resolve("附件/postgresql/micro-service-platform-ai-agent-harness.sql"));

        assertThat(mysql).contains("CREATE TABLE IF NOT EXISTS `ai_harness_operation`");
        assertThat(postgres).contains("CREATE TABLE IF NOT EXISTS ai_harness_operation");
        assertThat(mysql.toUpperCase()).doesNotContain("DROP TABLE");
        assertThat(postgres.toUpperCase()).doesNotContain("DROP TABLE");
    }

    @Test
    void mainMysqlBootstrapShouldUseOnlyNativeHarnessTables() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize().getParent();
        String sql = Files.readString(repository.resolve("附件/mysql/micro-service-platform-ai.sql"));

        assertThat(sql).contains(
                "-- LangChain4j 原生 Harness 治理表",
                "CREATE TABLE IF NOT EXISTS `ai_harness_operation`",
                "CREATE TABLE IF NOT EXISTS `ai_skill_activation`",
                "`toolset_id` varchar(255) NOT NULL",
                "`approval_target` varchar(500)",
                "`approval_change` varchar(1000)",
                "`approval_risk` varchar(500)",
                "`approval_expires_at` datetime(3)",
                "idx_harness_operation_toolset");
        assertThat(sql).doesNotContain(
                LEGACY_AGENT_RUN_TABLE,
                LEGACY_AGENT_RUN_STEP_TABLE,
                LEGACY_AGENT_APPROVAL_TABLE,
                LEGACY_AGENT_EVENT_TABLE,
                LEGACY_AGENT_FEEDBACK_TABLE,
                "Durable AI agent harness tables");
    }

    @Test
    void harnessManualScriptsShouldUseChineseDescriptions() throws IOException {
        Path repository = Path.of("..").toAbsolutePath().normalize().getParent();
        String mysql = Files.readString(repository.resolve("附件/mysql/micro-service-platform-ai-agent-harness.sql"));
        String postgres = Files.readString(repository.resolve("附件/postgresql/micro-service-platform-ai-agent-harness.sql"));

        assertThat(mysql).contains("受治理工具操作记录", "工具所属工具集标识", "执行隔离令牌", "技能激活台账");
        assertThat(postgres).contains("受治理工具操作记录", "工具所属工具集标识", "执行隔离令牌", "技能激活台账");
    }
}
