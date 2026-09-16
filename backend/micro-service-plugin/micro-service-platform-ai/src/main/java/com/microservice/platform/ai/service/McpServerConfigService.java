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

package com.microservice.platform.ai.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.db.mybatisplus.ext.SuperService;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigPageReq;
import com.microservice.platform.ai.domain.dto.req.McpServerConfigSaveReq;
import com.microservice.platform.ai.domain.dto.resp.McpConnectionTestResp;
import com.microservice.platform.ai.domain.dto.resp.McpServerConfigPageResp;
import com.microservice.platform.ai.domain.dto.resp.McpToolInfoResp;
import com.microservice.platform.ai.domain.entity.McpServer;

import java.util.List;

/**
 * MCP配置服务接口
 *
 * @author xJh
 * @since 2025/12/07
 */
public interface McpServerConfigService extends SuperService<McpServer> {

    /**
     * 分页查询
     * @param req 请求参数
     * @return 处理结果
     */
    IPage<McpServerConfigPageResp> pageList(McpServerConfigPageReq req);

    /**
     * 新增配置
     * @param req 请求参数
     */
    void create(McpServerConfigSaveReq req);

    /**
     * 修改配置
     * @param id 主键标识
     * @param req 请求参数
     */
    void modify(Long id, McpServerConfigSaveReq req);

    /**
     * 删除配置
     * @param id 主键标识
     */
    void remove(Long id);

    /**
     * 获取所有启用的配置
     * @return 启用的配置列表
     */
    List<McpServer> listEnabled();

    /**
     * 刷新连接
     * @param id 主键标识
     */
    void refresh(Long id);

    /**
     * 测试MCP连接
     * @param id 主键标识
     * @return 处理结果
     */
    McpConnectionTestResp testConnection(Long id);

    /**
     * 获取MCP工具列表
     * @param id 主键标识
     * @return 处理结果
     */
    List<McpToolInfoResp> getTools(Long id);

    /**
     * 切换启用状态
     * @param id 主键标识
     * @param status 状态
     */
    void toggleStatus(Long id, Boolean status);
}
