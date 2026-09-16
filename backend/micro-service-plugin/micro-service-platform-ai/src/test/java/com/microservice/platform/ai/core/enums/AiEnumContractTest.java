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

package com.microservice.platform.ai.core.enums;

import com.microservice.platform.ai.core.config.VectorStoreProperties;
import com.microservice.platform.ai.core.exception.AiErrorCode;
import com.microservice.platform.ai.core.workflow.WorkflowDiagnosticIssue;
import com.microservice.platform.ai.core.workflow.config.node.CodeNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.DocExtractorConfig;
import com.microservice.platform.ai.core.workflow.config.node.HttpRequestNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.IfElseNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.IterationNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.KnowledgeRetrievalConfig;
import com.microservice.platform.ai.core.workflow.config.node.ListOperatorConfig;
import com.microservice.platform.ai.core.workflow.config.node.ParallelNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.ParameterExtractorConfig;
import com.microservice.platform.ai.core.workflow.config.node.StartNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.TemplateNodeConfig;
import com.microservice.platform.ai.core.workflow.config.node.VariableAggregatorConfig;
import com.microservice.platform.ai.core.workflow.config.node.VariableAssignerConfig;
import com.microservice.platform.ai.core.workflow.enums.WorkflowCompareOperator;
import com.microservice.platform.ai.core.workflow.enums.WorkflowLogicalOperator;
import com.microservice.platform.ai.core.workflow.enums.WorkflowValueType;
import com.microservice.platform.ai.core.workflow.error.WorkflowExecutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI 模块枚举统一契约")
class AiEnumContractTest {

    private static final List<Class<? extends Enum<?>>> PUBLIC_ENUMS = List.of(
            NodeType.class,
            WorkflowStatus.class,
            ExecutionStatus.class,
            TemplateCategory.class,
            WorkflowPermissionRole.class,
            MessageRole.class,
            KnowledgeItemType.class,
            KnowledgeItemStatus.class,
            ConversationType.class,
            ChunkType.class);

    private static final List<Class<? extends Enum<?>>> ALL_AI_ENUMS = List.of(
            VectorStoreProperties.StoreType.class,
            VectorStoreProperties.CollectionNamingStrategy.class,
            ChunkType.class,
            ConversationType.class,
            ExecutionStatus.class,
            KnowledgeItemType.class,
            MessageRole.class,
            KnowledgeItemStatus.class,
            NodeType.class,
            WorkflowPermissionRole.class,
            TemplateCategory.class,
            WorkflowStatus.class,
            AiErrorCode.class,
            VectorizationTaskStatus.class,
            VectorizationTaskType.class,
            WorkflowDiagnosticIssue.Severity.class,
            WorkflowExecutionException.ErrorType.class,
            DocExtractorConfig.DocumentType.class,
            DocExtractorConfig.OcrEngine.class,
            CodeNodeConfig.CodeLanguage.class,
            ListOperatorConfig.OperationType.class,
            WorkflowLogicalOperator.class,
            WorkflowCompareOperator.class,
            ListOperatorConfig.SortDirection.class,
            ListOperatorConfig.KeepStrategy.class,
            KnowledgeRetrievalConfig.RetrievalMode.class,
            KnowledgeRetrievalConfig.FilterOperator.class,
            IterationNodeConfig.ProcessingMode.class,
            HttpRequestNodeConfig.HttpMethod.class,
            HttpRequestNodeConfig.BodyType.class,
            HttpRequestNodeConfig.AuthType.class,
            IfElseNodeConfig.BranchType.class,
            TemplateNodeConfig.TemplateEngine.class,
            ParameterExtractorConfig.InferenceMode.class,
            ParallelNodeConfig.WaitStrategy.class,
            StartNodeConfig.InputFieldType.class,
            WorkflowValueType.class,
            VariableAggregatorConfig.AggregationStrategy.class,
            VariableAssignerConfig.AssignmentType.class);

    @Test
    @DisplayName("所有公开枚举都实现 AiEnum")
    void allPublicEnumsImplementAiEnum() {
        for (Class<? extends Enum<?>> enumType : PUBLIC_ENUMS) {
            assertThat(AiEnum.class.isAssignableFrom(enumType))
                    .as(enumType.getSimpleName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("AI 后端全部枚举都实现 AiEnum")
    void allBackendEnumsImplementAiEnum() {
        for (Class<? extends Enum<?>> enumType : ALL_AI_ENUMS) {
            assertThat(AiEnum.class.isAssignableFrom(enumType))
                    .as(enumType.getName())
                    .isTrue();
        }
    }

    @Test
    @DisplayName("AI 后端枚举契约清单不能重复登记")
    void allBackendEnumsListHasNoDuplicates() {
        Set<Class<? extends Enum<?>>> uniqueTypes = new LinkedHashSet<>(ALL_AI_ENUMS);

        assertThat(uniqueTypes).hasSameSizeAs(ALL_AI_ENUMS);
    }

    @Test
    @DisplayName("所有 AI 后端枚举编码唯一且描述不为空")
    void allBackendEnumsHaveUniqueCodeAndDescription() {
        for (Class<? extends Enum<?>> enumType : ALL_AI_ENUMS) {
            List<AiEnum> values = Arrays.stream(enumType.getEnumConstants())
                    .map(AiEnum.class::cast)
                    .toList();

            assertThat(values).extracting(AiEnum::getCode).doesNotHaveDuplicates();
            assertThat(values).allSatisfy(item -> {
                assertThat(item.getCode()).isNotBlank();
                assertThat(item.getDescription()).isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("共享工作流枚举提供 fromCode 反查方法")
    void sharedWorkflowEnumsProvideFromCode() throws Exception {
        List<Class<? extends Enum<?>>> sharedEnums = List.of(
                WorkflowLogicalOperator.class,
                WorkflowCompareOperator.class,
                WorkflowValueType.class);

        for (Class<? extends Enum<?>> enumType : sharedEnums) {
            Method fromCode = enumType.getDeclaredMethod("fromCode", String.class);
            assertThat(Modifier.isStatic(fromCode.getModifiers())).isTrue();

            AiEnum first = (AiEnum) enumType.getEnumConstants()[0];
            Object parsed = fromCode.invoke(null, first.getCode());

            assertThat(parsed).isEqualTo(first);
        }
    }

    @Test
    @DisplayName("所有公开枚举 code 唯一且 description 不为空")
    void allPublicEnumsHaveUniqueCodeAndDescription() {
        for (Class<? extends Enum<?>> enumType : PUBLIC_ENUMS) {
            List<AiEnum> values = Arrays.stream(enumType.getEnumConstants())
                    .map(AiEnum.class::cast)
                    .toList();

            assertThat(values).extracting(AiEnum::getCode).doesNotHaveDuplicates();
            assertThat(values).allSatisfy(item -> {
                assertThat(item.getCode()).isNotBlank();
                assertThat(item.getDescription()).isNotBlank();
            });
        }
    }

    @Test
    @DisplayName("所有公开枚举提供 fromCode 反查方法")
    void allPublicEnumsProvideFromCode() throws Exception {
        for (Class<? extends Enum<?>> enumType : PUBLIC_ENUMS) {
            Method fromCode = enumType.getDeclaredMethod("fromCode", String.class);
            AiEnum first = (AiEnum) enumType.getEnumConstants()[0];
            Object parsed = fromCode.invoke(null, first.getCode());

            assertThat(parsed).isEqualTo(first);
        }
    }
}
