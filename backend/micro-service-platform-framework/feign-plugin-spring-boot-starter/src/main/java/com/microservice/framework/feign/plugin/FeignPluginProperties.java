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

package com.microservice.framework.feign.plugin;

import com.microservice.framework.feign.plugin.mock.MockProperties;
import feign.Logger;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Levin
 */
@Data
@ConfigurationProperties(prefix = FeignPluginProperties.PLUGIN_PREFIX)
public class FeignPluginProperties {

    public static final String PLUGIN_PREFIX = "extend.feign.plugin";
    private boolean enabled = true;
    /**
     * Header 白名单
     * 直接在这里初始化默认值
     */
    private List<String> allowedHeaders = new ArrayList<>();
    /**
     * Feign 日志级别。默认只记录方法、URL、状态和耗时，避免请求头与正文泄露凭证。
     */
    private Logger.Level level = Logger.Level.BASIC;
    private MockProperties mock = new MockProperties();

}
