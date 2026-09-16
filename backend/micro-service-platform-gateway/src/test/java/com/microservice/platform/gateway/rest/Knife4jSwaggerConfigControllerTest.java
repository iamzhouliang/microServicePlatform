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

package com.microservice.platform.gateway.rest;

import com.github.xingfudeshi.knife4j.spring.gateway.Knife4jGatewayProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class Knife4jSwaggerConfigControllerTest {
    
    @Test
    void shouldBuildManualSwaggerConfigWithoutKnife4jGatewayEndpoint() {
        Knife4jGatewayProperties properties = new Knife4jGatewayProperties();
        properties.getRoutes().add(route("Suite", "/suite/v3/api-docs?group=default", "/suite", 20));
        properties.getRoutes().add(route("IAM", "/iam/v3/api-docs?group=default", "/iam", 10));
        Knife4jSwaggerConfigController controller = new Knife4jSwaggerConfigController(properties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/v3/api-docs/swagger-config")
                .header(HttpHeaders.REFERER, "http://localhost:15000/doc.html")
                .build();
        
        ResponseEntity<Knife4jSwaggerConfigController.SwaggerConfigResponse> response = controller.swaggerConfig(request).block();
        
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        Knife4jSwaggerConfigController.SwaggerConfigResponse body = response.getBody();
        assertThat(body.configUrl()).isEqualTo("/v3/api-docs/swagger-config");
        assertThat(body.operationsSorter()).isEqualTo("alpha");
        assertThat(body.tagsSorter()).isEqualTo("alpha");
        List<Knife4jSwaggerConfigController.SwaggerResource> urls = body.urls();
        assertThat(urls).extracting(Knife4jSwaggerConfigController.SwaggerResource::name)
                .containsExactly("IAM", "Suite");
        assertThat(urls).extracting(Knife4jSwaggerConfigController.SwaggerResource::url)
                .containsExactly("/iam/v3/api-docs?group=default", "/suite/v3/api-docs?group=default");
        assertThat(urls).extracting(Knife4jSwaggerConfigController.SwaggerResource::contextPath)
                .containsExactly("/iam", "/suite");
    }
    
    @Test
    void shouldExposeSwaggerConfigOverHttp() {
        Knife4jGatewayProperties properties = new Knife4jGatewayProperties();
        properties.getRoutes().add(route("IAM", "/iam/v3/api-docs?group=default", "/iam", 10));
        Knife4jSwaggerConfigController controller = new Knife4jSwaggerConfigController(properties);
        WebTestClient webTestClient = WebTestClient.bindToController(controller).build();
        
        webTestClient.get()
                .uri("/v3/api-docs/swagger-config")
                .header(HttpHeaders.REFERER, "http://localhost:15000/doc.html")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.configUrl").isEqualTo("/v3/api-docs/swagger-config")
                .jsonPath("$.urls[0].name").isEqualTo("IAM")
                .jsonPath("$.urls[0].url").isEqualTo("/iam/v3/api-docs?group=default")
                .jsonPath("$.urls[0].contextPath").isEqualTo("/iam");
    }
    
    @Test
    void shouldPrefixUrlsWhenKnife4jRunsBehindContextPath() {
        Knife4jGatewayProperties properties = new Knife4jGatewayProperties();
        properties.getRoutes().add(route("IAM", "/iam/v3/api-docs?group=default", "/iam", 1));
        Knife4jSwaggerConfigController controller = new Knife4jSwaggerConfigController(properties);
        MockServerHttpRequest request = MockServerHttpRequest.get("/v3/api-docs/swagger-config")
                .header(HttpHeaders.REFERER, "http://localhost:15000/gateway/doc.html")
                .build();
        
        ResponseEntity<Knife4jSwaggerConfigController.SwaggerConfigResponse> response = controller.swaggerConfig(request).block();
        
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().urls()).singleElement().satisfies(resource -> {
            assertThat(resource.url()).isEqualTo("/gateway/iam/v3/api-docs?group=default");
            assertThat(resource.contextPath()).isEqualTo("/gateway/iam");
        });
    }
    
    private static Knife4jGatewayProperties.Router route(String name, String url, String contextPath, int order) {
        Knife4jGatewayProperties.Router route = new Knife4jGatewayProperties.Router();
        route.setName(name);
        route.setUrl(url);
        route.setContextPath(contextPath);
        route.setOrder(order);
        return route;
    }
    
}
