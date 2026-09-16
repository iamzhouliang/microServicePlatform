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
import com.microservice.platform.ai.domain.dto.req.ChatAgentPageReq;
import com.microservice.platform.ai.domain.dto.req.ChatAgentSaveReq;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentDetailResp;
import com.microservice.platform.ai.domain.dto.resp.ChatAgentPageResp;
import com.microservice.platform.ai.domain.entity.ChatAgent;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 智能体服务接口
 *
 * @author xJh
 * @since 2025/11/4
 **/
public interface ChatAgentService extends SuperService<ChatAgent> {

    /**
     * 分页查询智能体
     *
     * @param req 分页查询请求
     * @return 分页结果
     */
    IPage<ChatAgentPageResp> pageList(ChatAgentPageReq req);

    /**
     * 获取智能体详情
     *
     * @param id 智能体ID
     * @return 智能体详情
     */
    ChatAgentDetailResp detail(Long id);

    /**
     * 创建智能体
     *
     * @param req 创建请求
     */
    void create(ChatAgentSaveReq req);

    /**
     * 修改智能体
     *
     * @param id  智能体ID
     * @param req 修改请求
     */
    void modify(Long id, ChatAgentSaveReq req);

    /**
     * 删除智能体
     *
     * @param id 智能体ID
     */
    void remove(Long id);

    /**
     * 根据用户ID获取智能体列表
     *
     * @param userId 用户ID
     * @return 智能体列表
     */
    List<ChatAgentPageResp> listByUserId(Long userId);

    /**
     * 检查智能体名称是否存在
     *
     * @param name   智能体名称
     * @param userId 用户ID
     * @param id     排除的智能体ID（修改时使用）
     * @return 是否存在
     */
    boolean existsByName(String name, Long userId, Long id);

    String uploadAvatar(MultipartFile file);

    ChatAgentDetailResp detailByAgentId(Long agentId);

    List<ChatAgentPageResp> listByModelId(String modelId);
}
