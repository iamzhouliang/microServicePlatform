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
import com.microservice.platform.ai.domain.dto.req.SkillPageReq;
import com.microservice.platform.ai.domain.dto.req.SkillSaveReq;
import com.microservice.platform.ai.domain.dto.resp.SkillDetailResp;
import com.microservice.platform.ai.domain.dto.resp.SkillPageResp;
import com.microservice.platform.ai.domain.entity.AiSkill;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * AI技能服务
 *
 * @author xJh
 * @since 2026/06/24
 */
public interface SkillService extends SuperService<AiSkill> {

    IPage<SkillPageResp> pageList(SkillPageReq req);

    SkillDetailResp detail(Long id);

    SkillDetailResp preview(Long id);

    byte[] download(Long id);

    void updateFile(Long id, String path, String content);

    List<SkillDetailResp> listEnabled();

    void create(SkillSaveReq req, List<MultipartFile> files, List<String> relativePaths);

    void modify(Long id, SkillSaveReq req, List<MultipartFile> files, List<String> relativePaths);

    void remove(Long id);

    void toggleStatus(Long id, Boolean status);

    void togglePublished(Long id, Boolean published);
}
