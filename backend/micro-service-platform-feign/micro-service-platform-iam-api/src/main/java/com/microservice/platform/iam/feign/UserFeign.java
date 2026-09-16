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

package com.microservice.platform.iam.feign;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.microservice.framework.commons.FeignConstants;
import com.microservice.framework.commons.remote.LoadService;
import com.microservice.platform.iam.feign.domain.req.UserFeignPageReq;
import com.microservice.platform.iam.feign.domain.resp.UserInfoResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
import java.util.Set;

/**
 * @author Levin
 */
@FeignClient(name = FeignConstants.AUTH_FEIGN_NAME, dismiss404 = true)
public interface UserFeign extends LoadService<UserInfoResp> {

    /**
     * 根据 ID 批量查询
     *
     * @param ids 唯一键（可能不是主键ID)
     * @return 查询结果
     */
    @Override
    @PostMapping("/users/batch_ids")
    Map<Object, UserInfoResp> findByIds(@RequestBody Set<Object> ids);

    /**
     * 分页查询
     *
     * @param req req
     * @return 查询结果
     */
    @PostMapping("/users/page")
    Page<UserInfoResp> pageList(@RequestBody UserFeignPageReq req);

    /**
     * 根据 ID 批量查询
     *
     * @param id 唯一键（可能不是主键ID)
     * @return 查询结果
     */
    @PostMapping("/users/{id}")
    UserInfoResp findById(@PathVariable Long id);

}
