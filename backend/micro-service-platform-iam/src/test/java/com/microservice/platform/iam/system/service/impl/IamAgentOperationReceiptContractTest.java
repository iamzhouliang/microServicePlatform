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

package com.microservice.platform.iam.system.service.impl;

import com.baomidou.mybatisplus.annotation.TableName;
import com.microservice.framework.commons.entity.SuperEntity;
import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IAM 智能体操作回执持久化契约。
 *
 * @author OmX
 * @since 2026-07-15
 */
class IamAgentOperationReceiptContractTest {
    
    private static final String ENTITY =
            "com.microservice.platform.iam.system.domain.entity.IamAgentOperationReceipt";
    private static final String MAPPER =
            "com.microservice.platform.iam.system.repository.IamAgentOperationReceiptMapper";
    private static final String TABLE = "iam_agent_operation_receipt";
    
    @Test
    void receiptEntityAndMapperExposeTenantScopedLifecycleContract() {
        Class<?> entityType = assertDoesNotThrow(() -> Class.forName(ENTITY));
        
        assertTrue(SuperEntity.class.isAssignableFrom(entityType));
        TableName tableName = entityType.getAnnotation(TableName.class);
        assertNotNull(tableName);
        assertEquals(TABLE, tableName.value());
        for (String field : List.of("operationKey", "operationType", "status", "requestDigest", "username",
                "userId", "previousRoleIds", "responsePayload", "tenantId")) {
            assertDoesNotThrow(() -> entityType.getDeclaredField(field));
        }
        assertFalse(Pattern.compile("(?i).*(password|token|secret|credential).*")
                .matcher(List.of(entityType.getDeclaredFields()).toString()).find());
        
        Class<?> mapperType = assertDoesNotThrow(() -> Class.forName(MAPPER));
        ParameterizedType contract = (ParameterizedType) mapperType.getGenericInterfaces()[0];
        assertEquals(SuperMapper.class, contract.getRawType());
        assertEquals(entityType, contract.getActualTypeArguments()[0]);
    }
    
    @Test
    void manualAndCanonicalSqlDefineTheSameSafeReceiptTable() {
        Path manualPath = repositoryRoot().resolve("附件/mysql/micro-service-platform-iam-agent-operation.sql");
        Path canonicalPath = repositoryRoot().resolve("附件/mysql/micro-service-platform.sql");
        assertTrue(Files.isRegularFile(manualPath), "缺少 IAM 智能体操作回执手工建表脚本");
        
        String manual = assertDoesNotThrow(() -> Files.readString(manualPath));
        String canonical = assertDoesNotThrow(() -> Files.readString(canonicalPath));
        String manualDefinition = tableDefinition(manual);
        String canonicalDefinition = tableDefinition(canonical);
        
        assertFalse(manualDefinition.isBlank());
        assertEquals(manualDefinition, canonicalDefinition);
        assertTrue(normalize(manual).contains("create table if not exists `" + TABLE + "`"));
        assertContains(manualDefinition,
                "`operation_key` varchar(128) not null",
                "`operation_type` varchar(32) not null",
                "`status` varchar(32) not null",
                "`request_digest` varchar(64)",
                "`username` varchar(30) not null",
                "`user_id` bigint",
                "`previous_role_ids` text",
                "`response_payload` text",
                "`tenant_id` bigint not null",
                "unique key `uk_iam_agent_operation_tenant_key` (`tenant_id`, `operation_key`)",
                "`deleted` tinyint(1)", "`create_by` bigint", "`create_name` varchar(64)",
                "`create_time` datetime", "`last_modify_by` bigint", "`last_modify_name` varchar(64)",
                "`last_modify_time` datetime");
        assertFalse(Pattern.compile("`(?:password|token|secret|credential)[^`]*`").matcher(manualDefinition).find());
    }
    
    @Test
    void manualSqlAlignsUserColumnsRequiredByTheIamEntity() {
        Path manualPath = repositoryRoot().resolve("附件/mysql/micro-service-platform-iam-agent-operation.sql");
        String manual = normalize(assertDoesNotThrow(() -> Files.readString(manualPath)));
        
        assertContains(manual,
                "information_schema.columns",
                "column_name = 'type'",
                "add column `type` varchar(255)",
                "column_name = 'nick_name'",
                "change column `nick_name` `nickname` varchar(50)",
                "@iam_agent_nick_name_exists = 1 and @iam_agent_nickname_exists = 1",
                "update `t_user` set `nickname` = `nick_name`");
    }
    
    @Test
    void manualSqlRepairsMissingOrDriftedRequestDigestColumn() {
        Path manualPath = repositoryRoot().resolve("附件/mysql/micro-service-platform-iam-agent-operation.sql");
        String manual = normalize(assertDoesNotThrow(() -> Files.readString(manualPath)));
        
        assertContains(manual,
                "column_name = 'request_digest'",
                "add column `request_digest` varchar(64)",
                "character_maximum_length = 64",
                "modify column `request_digest` varchar(64)");
    }
    
    @Test
    void manualSqlRepairsRoleAssignmentReceiptColumns() {
        Path manualPath = repositoryRoot().resolve("附件/mysql/micro-service-platform-iam-agent-operation.sql");
        String manual = normalize(assertDoesNotThrow(() -> Files.readString(manualPath)));
        
        assertContains(manual,
                "column_name = 'previous_role_ids'",
                "add column `previous_role_ids` text",
                "column_name = 'response_payload'",
                "add column `response_payload` text");
    }
    
    @Test
    void postgresqlSqlDefinesIdempotentTenantScopedReceiptTable() {
        Path postgresqlPath = repositoryRoot().resolve("附件/postgresql/micro-service-platform-iam-agent-operation.sql");
        assertTrue(Files.isRegularFile(postgresqlPath), "缺少 PostgreSQL IAM 智能体操作回执脚本");
        
        String sql = normalize(assertDoesNotThrow(() -> Files.readString(postgresqlPath)));
        assertContains(sql,
                "create table if not exists " + TABLE,
                "operation_key varchar(128) not null",
                "operation_type varchar(32) not null",
                "status varchar(32) not null",
                "request_digest varchar(64)",
                "username varchar(30) not null",
                "user_id bigint",
                "previous_role_ids text",
                "response_payload text",
                "tenant_id bigint not null",
                "unique index if not exists uk_iam_agent_operation_tenant_key",
                "(tenant_id, operation_key)",
                "add column if not exists request_digest varchar(64)",
                "add column if not exists previous_role_ids text",
                "add column if not exists response_payload text");
        assertFalse(Pattern.compile("(?:password|token|secret|credential)[a-z0-9_]*\\s")
                .matcher(sql).find());
    }
    
    private static void assertContains(String sql, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), "缺少 SQL 契约: " + fragment);
        }
    }
    
    private static String tableDefinition(String sql) {
        Matcher matcher = Pattern.compile("create table(?: if not exists)? `" + TABLE
                + "` \\((.*?)\\) engine=innodb", Pattern.DOTALL).matcher(normalize(sql));
        return matcher.find() ? normalize(matcher.group(1)) : "";
    }
    
    private static String normalize(String sql) {
        return sql.toLowerCase(Locale.ROOT)
                .replaceAll("/\\*.*?\\*/", " ")
                .replaceAll("--[^\\r\\n]*", " ")
                .replaceAll("\\s+", " ")
                .replaceAll("\\s*,\\s*", ", ")
                .replaceAll("\\(\\s+", "(")
                .replaceAll("\\s+\\)", ")")
                .trim();
    }
    
    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        return Files.isDirectory(current.resolve("micro-service-platform-iam")) ? current : current.resolve("..").normalize();
    }
}
