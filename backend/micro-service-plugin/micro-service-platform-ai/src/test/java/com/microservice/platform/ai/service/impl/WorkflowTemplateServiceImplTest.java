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

package com.microservice.platform.ai.service.impl;

import com.microservice.platform.ai.core.workflow.WorkflowValidationResult;
import com.microservice.platform.ai.core.workflow.WorkflowValidator;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AI 工作流内置模板")
class WorkflowTemplateServiceImplTest {

    private final WorkflowValidator validator = new WorkflowValidator();

    @Test
    @DisplayName("全部内置模板图都可以通过强诊断")
    @SuppressWarnings("unchecked")
    void builtInTemplatesPassWorkflowValidation() {
        WorkflowTemplateServiceImpl service = new WorkflowTemplateServiceImpl(null, null, null);

        List<WorkflowTemplate> templates = (List<WorkflowTemplate>) ReflectionTestUtils.invokeMethod(service, "createPredefinedTemplates");

        assertThat(templates).hasSize(5);
        for (WorkflowTemplate template : templates) {
            WorkflowValidationResult result = validator.validate(template.getGraph());
            assertThat(result.getErrors())
                    .as("模板 [%s] 应可从模板创建为工作流", template.getName())
                    .isEmpty();
        }
    }
}
