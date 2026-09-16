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

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Harness 大结果外置存储 SPI。
 *
 * <p>业务系统可以替换为对象存储实现，但必须保持租户隔离、不可猜测引用和完整性摘要。</p>
 *
 * @author xJh
 * @since 2026-07-18
 */
public interface HarnessSpillStore {
    
    SpillReference write(SpillWriteRequest request);
    
    Optional<byte[]> read(String tenantId, String uri);
    
    int cleanupExpired();
    
    /**
     * 待写入的大结果。
     *
     * @param tenantId 租户命名空间
     * @param runId 运行或 Operation 标识
     * @param stepId 步骤或 Tool Call 标识
     * @param content 完整结果内容
     */
    record SpillWriteRequest(String tenantId, String runId, String stepId, byte[] content) {

        public SpillWriteRequest {
            Objects.requireNonNull(tenantId, "租户命名空间不能为空");
            Objects.requireNonNull(runId, "运行命名空间不能为空");
            Objects.requireNonNull(stepId, "步骤命名空间不能为空");
            content = Objects.requireNonNull(content, "大结果内容不能为空").clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
    
    /**
     * 可持久化的证据引用。
     *
     * @param uri 不可猜测的证据地址
     * @param contentDigest 内容完整性摘要
     * @param contentSize 原始内容字节数
     * @param expiresAt 证据过期时间
     */
    record SpillReference(String uri, String contentDigest, long contentSize, Instant expiresAt) {

        public SpillReference {
            Objects.requireNonNull(uri, "大结果引用不能为空");
            Objects.requireNonNull(contentDigest, "大结果摘要不能为空");
            Objects.requireNonNull(expiresAt, "大结果过期时间不能为空");
        }
    }
}
