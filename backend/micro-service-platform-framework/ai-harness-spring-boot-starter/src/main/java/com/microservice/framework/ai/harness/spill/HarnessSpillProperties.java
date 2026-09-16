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

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Harness 大结果外置配置。
 *
 * @author xJh
 * @since 2026-07-18
 */
@Data
@ConfigurationProperties(prefix = "micro-service.ai.harness.spill")
public class HarnessSpillProperties {
    
    /** 是否启用 Tool Result Spill。 */
    private boolean enabled = true;
    
    /** 单个结果允许内联持久化的最大字节数。 */
    private int thresholdBytes = 64 * 1024;
    
    /** 持久化投影的最大预览字符数。 */
    private int previewCharacters = 1_024;
    
    /** 默认文件存储目录。 */
    private Path directory = Path.of("data", "ai-harness-spill");
    
    /** 外置结果保留时间。 */
    private Duration ttl = Duration.ofDays(7);
}
