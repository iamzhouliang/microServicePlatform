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

package com.microservice.platform.iam;

import cn.hutool.core.lang.Assert;
import com.microservice.framework.boot.remote.dict.DictLoadService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import java.util.Map;

/**
 * 远程Redis字典加载测试
 *
 * @author YanCh
 */
@Slf4j
@SpringBootTest(classes = IamApplication.class)
public class RemoteAloneRedisTest {

    /** 测试上下文没有真实 Servlet 容器，替换端点导出器以避免加载无关 WebSocket 基础设施。 */
    @MockitoBean
    private ServerEndpointExporter serverEndpointExporter;

    @Resource
    private DictLoadService dictLoadService;

    @Test
    void shouldLoadDictByCode() {
        Map<Object, Object> map = dictLoadService.findByIds("AREA_LEVEL");
        Assert.notNull(map, "字典加载失败");
        log.info("字典加载结果: {}", map);
    }
}
