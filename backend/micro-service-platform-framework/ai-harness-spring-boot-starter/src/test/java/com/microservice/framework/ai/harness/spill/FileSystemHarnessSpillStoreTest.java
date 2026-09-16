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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemHarnessSpillStoreTest {
    
    @TempDir
    private Path root;
    
    @Test
    void isolatesTenantAndRejectsUnsafeNamespaces() {
        FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(
                root, Duration.ofDays(1), Clock.systemUTC());
        HarnessSpillStore.SpillReference reference = store.write(
                new HarnessSpillStore.SpillWriteRequest("tenant-a", "run-1", "step-1", "内容".getBytes()));
        
        assertThat(store.read("tenant-a", reference.uri())).hasValue("内容".getBytes());
        assertThatThrownBy(() -> store.read("tenant-b", reference.uri()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("租户");
        assertThatThrownBy(() -> store.write(new HarnessSpillStore.SpillWriteRequest(
                "../escape", "run-1", "step-1", new byte[]{1})))
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessageContaining("命名空间");
    }
    
    @Test
    void cleansExpiredSpillFilesByTtl() {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        final FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(root, Duration.ofHours(1), clock);
        HarnessSpillStore.SpillReference reference = store.write(
                new HarnessSpillStore.SpillWriteRequest("tenant-a", "run-1", "step-1", new byte[]{1, 2, 3}));
        
        clock.instant = clock.instant.plus(Duration.ofHours(2));
        
        assertThat(store.cleanupExpired()).isOne();
        assertThat(store.read("tenant-a", reference.uri())).isEmpty();
    }
    
    @Test
    void preservesExpiredJsonFilesOutsideManagedSpillLayout() throws IOException {
        MutableClock clock = new MutableClock(Instant.parse("2026-07-17T00:00:00Z"));
        Path unrelatedFile = root.resolve("shared").resolve("settings.json");
        Files.createDirectories(unrelatedFile.getParent());
        Files.writeString(unrelatedFile, "普通应用配置");
        Files.setLastModifiedTime(unrelatedFile, FileTime.from(clock.instant));
        
        clock.instant = clock.instant.plus(Duration.ofHours(2));
        
        FileSystemHarnessSpillStore store = new FileSystemHarnessSpillStore(root, Duration.ofHours(1), clock);
        assertThat(store.cleanupExpired()).isZero();
        assertThat(unrelatedFile).exists();
    }
    
    private static final class MutableClock extends Clock {
        
        private Instant instant;
        
        private MutableClock(Instant instant) {
            this.instant = instant;
        }
        
        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }
        
        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
        
        @Override
        public Instant instant() {
            return instant;
        }
    }
}
