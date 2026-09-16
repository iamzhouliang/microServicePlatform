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

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class AiSkillMigrationContractTest {

    @Test
    void v8DelistsLegacyDigestsAndMigratesUniqueKeyIdempotently() throws IOException {
        String sql = readModuleFile("src/main/resources/db/migration/V8__add_ai_skill_metadata.sql");
        String normalized = sql.toLowerCase(Locale.ROOT);

        assertThat(normalized)
                .contains("update `ai_skill`")
                .contains("`published` = 0")
                .contains("content_digest")
                .contains("regexp")
                .contains("information_schema.statistics")
                .contains("drop index `uk_skill_code`")
                .contains("unique index `uk_skill_code_version` (`code`, `version`)");
        assertThat(normalized).doesNotContain("binary `content_digest` not regexp");
    }

    @Test
    void v8FailsWithCleanupMessageWhenCodeVersionPairsAreDuplicated() throws IOException {
        String normalized = readModuleFile("src/main/resources/db/migration/V8__add_ai_skill_metadata.sql")
                .toLowerCase(Locale.ROOT);

        assertThat(normalized)
                .contains("group by `code`, `version`")
                .contains("having count(*) > 1")
                .contains("signal sqlstate '45000'")
                .contains("请先清理重复的技能编码和版本");
    }

    @Test
    void v8ValidatesNamedCompoundIndexStructureBeforeReusingIt() throws IOException {
        String normalized = readModuleFile("src/main/resources/db/migration/V8__add_ai_skill_metadata.sql")
                .toLowerCase(Locale.ROOT);

        assertThat(normalized)
                .contains("non_unique")
                .contains("seq_in_index")
                .contains("column_name")
                .contains("count(*) = 2")
                .contains("drop index `uk_skill_code_version`")
                .contains("add unique index `uk_skill_code_version` (`code`, `version`)");
    }

    @Test
    void canonicalSchemasUseCodeAndVersionUniqueKey() throws IOException {
        for (String relativePath : new String[]{"附件/mysql/micro-service-platform-ai.sql", "附件/mysql/micro-service-platform.sql"}) {
            String sql = readRepositoryFile(relativePath).toLowerCase(Locale.ROOT);
            assertThat(sql).contains("unique key `uk_skill_code_version` (`code`, `version`)");
            assertThat(sql).doesNotContain("unique key `uk_skill_code` (`code`)");
        }
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
