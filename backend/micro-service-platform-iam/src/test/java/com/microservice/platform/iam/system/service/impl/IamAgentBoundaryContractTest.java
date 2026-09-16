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

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.microservice.platform.iam.feign.agent.IamAgentFeign;
import com.microservice.platform.iam.feign.agent.domain.req.AgentUserCreateReq;
import com.microservice.platform.iam.system.controller.IamAgentController;
import com.microservice.platform.iam.system.repository.UserRoleMapper;
import com.microservice.platform.iam.tenant.service.impl.TenantServiceImpl;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author OmX
 */
class IamAgentBoundaryContractTest {

    private static jakarta.validation.ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void requiresDelegatedAuthorizationHeaderOnEveryFeignOperation() {
        Method[] operations = IamAgentFeign.class.getDeclaredMethods();

        assertEquals(8, operations.length);
        for (Method operation : operations) {
            RequestHeader header = Arrays.stream(operation.getParameters())
                    .map(parameter -> parameter.getAnnotation(RequestHeader.class))
                    .filter(annotation -> annotation != null && isAuthorizationHeader(annotation))
                    .findFirst()
                    .orElse(null);
            assertNotNull(header, () -> operation.getName() + " 必须声明 Authorization 请求头");
            assertTrue(header.required(), () -> operation.getName() + " 必须拒绝缺失的 Authorization 请求头");
        }
    }

    @Test
    void rejectsMissingCanonicalCreateUserFields() {
        AgentUserCreateReq req = new AgentUserCreateReq();
        req.setUsername(" ");
        req.setNickname("");
        req.setMobile(null);

        assertEquals(Set.of("operationKey", "username", "nickname", "mobile", "orgId"), invalidProperties(req));
    }

    @Test
    void rejectsOversizedAndMalformedCanonicalCreateUserFields() {
        AgentUserCreateReq req = new AgentUserCreateReq();
        req.setUsername("u".repeat(31));
        req.setNickname("n".repeat(51));
        req.setMobile("12345678901");
        req.setEmail("e".repeat(256));
        req.setOrgId(11L);
        req.setOperationKey("run-1:create-user@1.0.0");

        assertEquals(Set.of("username", "nickname", "mobile", "email"), invalidProperties(req));
    }

    @Test
    void rejectsOversizedOrUnsafeOperationKeys() {
        AgentUserCreateReq oversized = validCreateRequest();
        oversized.setOperationKey("a".repeat(129));
        AgentUserCreateReq unsafe = validCreateRequest();
        unsafe.setOperationKey("Bearer secret value");

        assertEquals(Set.of("operationKey"), invalidProperties(oversized));
        assertEquals(Set.of("operationKey"), invalidProperties(unsafe));
    }

    @Test
    void cascadesCreateUserValidationAtFeignAndControllerBoundaries() throws NoSuchMethodException {
        Method feignMethod = IamAgentFeign.class.getMethod("createUser", String.class, AgentUserCreateReq.class);
        Method controllerMethod = IamAgentController.class.getMethod("createUser", String.class, AgentUserCreateReq.class);

        assertNotNull(feignMethod.getParameters()[1].getAnnotation(Valid.class));
        assertNotNull(controllerMethod.getParameters()[1].getAnnotation(Valid.class));
    }

    @Test
    void createsAndReadsBackUserInOneTransaction() throws NoSuchMethodException {
        Method createUser = IamAgentServiceImpl.class.getMethod("createUser", AgentUserCreateReq.class);

        DSTransactional transactional = createUser.getAnnotation(DSTransactional.class);
        assertNotNull(transactional);
        assertTrue(Arrays.asList(transactional.rollbackFor()).contains(Exception.class));
    }

    @Test
    void exposesDeleteAsAnExplicitAuthorizedCompensationBoundary() throws NoSuchMethodException {
        Method feignDelete = IamAgentFeign.class.getMethod("deleteUser", String.class, Long.class);
        Method controllerDelete = IamAgentController.class.getMethod("deleteUser", String.class, Long.class);

        assertEquals("/agent/iam/users/{userId}", feignDelete.getAnnotation(DeleteMapping.class).value()[0]);
        assertTrue(Arrays.asList(controllerDelete.getAnnotation(SaCheckPermission.class).value())
                .contains("sys:user:remove"));
    }

    @Test
    void centralizesRuntimeUserRoleMutationsInUserRoleService() {
        for (Class<?> serviceType : Set.of(IamAgentServiceImpl.class, RoleServiceImpl.class,
                RoleResServiceImpl.class, UserServiceImpl.class, TenantServiceImpl.class)) {
            assertTrue(Arrays.stream(serviceType.getDeclaredFields())
                    .noneMatch(field -> field.getType().equals(UserRoleMapper.class)),
                    () -> serviceType.getSimpleName() + " 不得绕过用户角色变更边界");
        }
    }

    private boolean isAuthorizationHeader(RequestHeader header) {
        return HttpHeaders.AUTHORIZATION.equals(header.value()) || HttpHeaders.AUTHORIZATION.equals(header.name());
    }

    private Set<String> invalidProperties(AgentUserCreateReq req) {
        return validator.validate(req).stream().map(ConstraintViolation::getPropertyPath)
                .map(Object::toString).collect(Collectors.toSet());
    }

    private AgentUserCreateReq validCreateRequest() {
        return AgentUserCreateReq.builder().operationKey("run-1:create-user@1.0.0")
                .username("agent_user").nickname("代理用户").mobile("13800138000").orgId(11L).build();
    }

}
