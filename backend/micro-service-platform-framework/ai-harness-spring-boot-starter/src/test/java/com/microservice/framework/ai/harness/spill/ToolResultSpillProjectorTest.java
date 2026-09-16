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

package com.microservice.framework.ai.harness.spill;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolResultSpillProjectorTest {
    
    @TempDir
    private Path root;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Test
    void keepsSmallPayloadInlineAndSpillsLargePayloadWithEvidence() throws Exception {
        FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(
                root, Duration.ofDays(1), Clock.systemUTC());
        ToolResultSpillProjector projector = new ToolResultSpillProjector(objectMapper, store, 80, 24);
        Map<String, Object> small = Map.of("value", "ok");
        Map<String, Object> large = Map.of("items", "测试内容".repeat(100));
        
        assertThat(projector.project("1", "10", "step-a", small).payload()).isEqualTo(small);
        ToolResultSpillProjector.Projection projected = projector.project("1", "10", "step-b", large);
        
        assertThat(projected.spilled()).isTrue();
        assertThat(projected.payload()).containsKeys(
                "spillReference", "contentDigest", "contentSize", "preview");
        String reference = String.valueOf(projected.payload().get("spillReference"));
        byte[] restored = store.read("1", reference).orElseThrow();
        assertThat(objectMapper.readValue(restored, Map.class)).isEqualTo(large);
    }
    
    @Test
    void failsClosedWhenSpillWriteFails() {
        HarnessSpillStore failingStore = new HarnessSpillStore() {
            
            @Override
            public SpillReference write(SpillWriteRequest request) {
                throw new IllegalStateException("磁盘不可用");
            }
            
            @Override
            public Optional<byte[]> read(String tenantId, String uri) {
                return Optional.empty();
            }
            
            @Override
            public int cleanupExpired() {
                return 0;
            }
        };
        ToolResultSpillProjector projector = new ToolResultSpillProjector(objectMapper, failingStore, 1, 8);
        
        assertThatThrownBy(() -> projector.project("1", "10", "step-a", Map.of("value", "large")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("大结果持久化失败");
    }
    
    @Test
    void keepsLegalNullValuesForInlineBusinessResults() {
        FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(
                root, Duration.ofDays(1), Clock.systemUTC());
        ToolResultSpillProjector projector = new ToolResultSpillProjector(objectMapper, store, 80, 24);
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("optionalValue", null);
        
        assertThat(projector.project("1", "10", "step-a", payload).payload())
                .containsEntry("optionalValue", null);
    }
    
    @Test
    void projectsLargeTextResultToEvidencePointerForModelAndPersistence() {
        FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(
                root, Duration.ofDays(1), Clock.systemUTC());
        ToolResultSpillProjector projector = new ToolResultSpillProjector(objectMapper, store, 32, 12);
        String original = "大结果".repeat(100);
        
        ToolResultSpillProjector.TextProjection projection =
                projector.projectText("1", "operation-1", "call-1", original);
        
        assertThat(projection.spilled()).isTrue();
        assertThat(projection.resultText()).contains("spillReference", "contentDigest").doesNotContain(original);
        assertThat(new String(store.read("1", projection.evidenceReference()).orElseThrow(),
                java.nio.charset.StandardCharsets.UTF_8)).isEqualTo(original);
    }
}
