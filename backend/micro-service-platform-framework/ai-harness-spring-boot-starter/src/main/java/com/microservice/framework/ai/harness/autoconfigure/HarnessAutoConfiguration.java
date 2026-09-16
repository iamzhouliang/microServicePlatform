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

package com.microservice.framework.ai.harness.autoconfigure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.microservice.framework.ai.harness.capability.DefaultToolsetAvailabilityPolicy;
import com.microservice.framework.ai.harness.capability.ToolsetAvailabilityPolicy;
import com.microservice.framework.ai.harness.capability.ToolsetContributor;
import com.microservice.framework.ai.harness.capability.ToolsetResolver;
import com.microservice.framework.ai.harness.persistence.repository.HarnessOperationMapper;
import com.microservice.framework.ai.harness.persistence.repository.MybatisHarnessOperationStore;
import com.microservice.framework.ai.harness.observability.HarnessTraceRecorder;
import com.microservice.framework.ai.harness.observability.MicrometerHarnessTraceRecorder;
import io.micrometer.observation.ObservationRegistry;
import com.microservice.framework.ai.harness.runtime.HarnessOperationCoordinator;
import com.microservice.framework.ai.harness.runtime.HarnessOperationStore;
import com.microservice.framework.ai.harness.runtime.HarnessVerifierResolver;
import com.microservice.framework.ai.harness.runtime.PendingActionService;
import com.microservice.framework.ai.harness.runtime.ApprovalSummaryProjector;
import com.microservice.framework.ai.harness.spill.FileSystemHarnessSpillStore;
import com.microservice.framework.ai.harness.spill.HarnessSpillProperties;
import com.microservice.framework.ai.harness.spill.HarnessSpillStore;
import com.microservice.framework.ai.harness.spill.ToolResultSpillProjector;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.List;

/**
 * Agent Harness 默认自动配置。
 *
 * <p>Framework 只装配通用运行时。规划模型、能力 Provider 与 LangChain4j Tool 目录由业务应用提供，
 * 从而保持依赖方向单向且允许业务覆盖任意默认实现。</p>
 *
 * @author xiao1
 * @since 2026-07
 */
@AutoConfiguration
@EnableConfigurationProperties(HarnessSpillProperties.class)
@MapperScan(basePackages = "com.microservice.framework.ai.harness.persistence.repository",
        annotationClass = Repository.class)
public class HarnessAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ToolsetAvailabilityPolicy toolsetAvailabilityPolicy() {
        return new DefaultToolsetAvailabilityPolicy();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ToolsetResolver toolsetResolver(List<ToolsetContributor> contributors,
                                           List<ToolsetAvailabilityPolicy> policies) {
        return new ToolsetResolver(contributors, policies);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HarnessOperationStore harnessOperationStore(HarnessOperationMapper mapper) {
        return new MybatisHarnessOperationStore(mapper);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public PendingActionService pendingActionService(HarnessOperationStore store) {
        return new PendingActionService(store, Clock.systemUTC());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HarnessVerifierResolver harnessVerifierResolver() {
        return governance -> Optional.empty();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HarnessOperationCoordinator harnessOperationCoordinator(HarnessOperationStore store,
                                                                   PendingActionService pendingActionService, HarnessVerifierResolver verifierResolver,
                                                                   HarnessTraceRecorder traceRecorder, ToolResultSpillProjector spillProjector,
                                                                   ApprovalSummaryProjector approvalSummaryProjector) {
        return new HarnessOperationCoordinator(store, pendingActionService, verifierResolver,
                Clock.systemUTC(), Duration.ofSeconds(30), traceRecorder, spillProjector, approvalSummaryProjector);
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ApprovalSummaryProjector approvalSummaryProjector() {
        return ApprovalSummaryProjector.generic();
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HarnessSpillStore harnessSpillStore(HarnessSpillProperties properties, Environment environment) {
        if (properties.isEnabled() && environment.acceptsProfiles(Profiles.of("prod", "production"))) {
            throw new IllegalStateException("生产环境禁止使用本地文件 Spill，请配置对象存储 HarnessSpillStore");
        }
        return new FileSystemHarnessSpillStore(properties.getDirectory(), properties.getTtl(), Clock.systemUTC());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public ToolResultSpillProjector toolResultSpillProjector(ObjectMapper objectMapper,
                                                             HarnessSpillStore spillStore, HarnessSpillProperties properties) {
        if (!properties.isEnabled()) {
            return ToolResultSpillProjector.inlineOnly(objectMapper);
        }
        return new ToolResultSpillProjector(objectMapper, spillStore,
                properties.getThresholdBytes(), properties.getPreviewCharacters());
    }
    
    @Bean
    @ConditionalOnMissingBean
    public HarnessTraceRecorder harnessTraceRecorder(ObjectProvider<ObservationRegistry> registryProvider) {
        ObservationRegistry registry = registryProvider.getIfAvailable();
        return registry == null ? HarnessTraceRecorder.noop() : new MicrometerHarnessTraceRecorder(registry);
    }
    
}
