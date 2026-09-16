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

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.WorkflowStatus;
import com.microservice.platform.ai.core.workflow.WorkflowValidationResult;
import com.microservice.platform.ai.core.workflow.WorkflowValidator;
import com.microservice.platform.ai.domain.dto.req.WorkflowPageReq;
import com.microservice.platform.ai.domain.dto.req.WorkflowSaveReq;
import com.microservice.platform.ai.domain.dto.resp.WorkflowDetailResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowPageResp;
import com.microservice.platform.ai.domain.dto.resp.WorkflowVersionResp;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowDefinition;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowGraph;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowTemplate;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowVersion;
import com.microservice.platform.ai.repository.workflow.WorkflowDefinitionMapper;
import com.microservice.platform.ai.repository.workflow.WorkflowTemplateMapper;
import com.microservice.platform.ai.repository.workflow.WorkflowVersionMapper;
import com.microservice.platform.ai.service.WorkflowDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 工作流定义服务实现类
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowDefinitionServiceImpl extends SuperServiceImpl<WorkflowDefinitionMapper, WorkflowDefinition>
        implements
            WorkflowDefinitionService {

    /**
     * 最大保留版本数
     */
    private static final int MAX_VERSION_HISTORY = 20;

    private final AuthenticationContext context;
    private final WorkflowVersionMapper workflowVersionMapper;
    private final WorkflowTemplateMapper workflowTemplateMapper;
    private final WorkflowValidator workflowValidator;

    @Override
    public IPage<WorkflowPageResp> pageList(WorkflowPageReq req) {
        return baseMapper.selectPage(req.buildPage(), Wraps.<WorkflowDefinition>lbQ()
                .like(StringUtils.hasText(req.getName()), WorkflowDefinition::getName, req.getName())
                .eq(StringUtils.hasText(req.getStatus()), WorkflowDefinition::getStatus, req.getStatus())
                .eq(WorkflowDefinition::getUserId, context.userId())
                .orderByDesc(WorkflowDefinition::getLastModifyTime))
                .convert(this::convertToPageResp);
    }

    @Override
    public WorkflowDetailResp detail(Long id) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));
        return convertToDetailResp(workflow);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(WorkflowSaveReq req) {
        // 验证工作流图（如果有）
        if (req.getGraph() != null) {
            validateGraph(req.getGraph());
        }

        // 创建工作流定义
        WorkflowDefinition workflow = WorkflowDefinition.builder()
                .name(req.getName())
                .description(req.getDescription())
                .graph(req.getGraph())
                .inputVariables(req.getInputVariables())
                .outputVariables(req.getOutputVariables())
                .currentVersion(1)
                .status(WorkflowStatus.DRAFT.getCode())
                .userId(context.userId())
                .tenantId(context.tenantId())
                .build();

        baseMapper.insert(workflow);
        log.info("创建工作流成功，ID: {}, 名称: {}", workflow.getId(), workflow.getName());

        // 创建初始版本
        createVersion(workflow.getId(), 1, req.getGraph(),
                StringUtils.hasText(req.getChangeLog()) ? req.getChangeLog() : "初始版本");

        return workflow.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, WorkflowSaveReq req) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 检查权限
        checkOwnership(workflow);

        // 验证工作流图（如果有）
        if (req.getGraph() != null) {
            validateGraph(req.getGraph());
        }

        // 检查是否有实际变更（图定义变更才创建新版本）
        final boolean graphChanged = hasGraphChanged(workflow.getGraph(), req.getGraph());

        // 更新工作流定义
        workflow.setName(req.getName());
        workflow.setDescription(req.getDescription());
        workflow.setGraph(req.getGraph());
        workflow.setInputVariables(req.getInputVariables());
        workflow.setOutputVariables(req.getOutputVariables());

        // 如果图定义有变更，创建新版本
        if (graphChanged) {
            int newVersion = workflow.getCurrentVersion() + 1;
            workflow.setCurrentVersion(newVersion);

            // 创建新版本
            createVersion(id, newVersion, req.getGraph(),
                    StringUtils.hasText(req.getChangeLog()) ? req.getChangeLog() : "版本 " + newVersion);

            // 清理旧版本（保留最近 MAX_VERSION_HISTORY 个）
            cleanupOldVersions(id);
        }

        baseMapper.updateById(workflow);
        log.info("更新工作流成功，ID: {}, 名称: {}, 版本: {}", id, workflow.getName(), workflow.getCurrentVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 检查权限
        checkOwnership(workflow);

        // 删除版本历史
        workflowVersionMapper.delete(Wraps.<WorkflowVersion>lbQ()
                .eq(WorkflowVersion::getWorkflowId, id));

        // 删除工作流定义
        baseMapper.deleteById(id);
        log.info("删除工作流成功，ID: {}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 检查权限
        checkOwnership(workflow);

        // 验证工作流图
        if (workflow.getGraph() == null) {
            throw CheckedException.badRequest("工作流图不能为空");
        }
        WorkflowValidationResult result = workflowValidator.validate(workflow.getGraph());
        if (!result.isValid()) {
            throw CheckedException.badRequest("工作流验证失败: " + String.join("; ", result.getErrors()));
        }

        // 更新状态为已发布
        workflow.setStatus(WorkflowStatus.PUBLISHED.getCode());
        baseMapper.updateById(workflow);

        // 标记当前版本为已发布
        WorkflowVersion currentVersion = workflowVersionMapper.selectByWorkflowIdAndVersion(id, workflow.getCurrentVersion());
        if (currentVersion != null) {
            currentVersion.setPublished(true);
            workflowVersionMapper.updateById(currentVersion);
        }

        log.info("发布工作流成功，ID: {}, 版本: {}", id, workflow.getCurrentVersion());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archive(Long id) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 检查权限
        checkOwnership(workflow);

        // 更新状态为已归档
        workflow.setStatus(WorkflowStatus.ARCHIVED.getCode());
        baseMapper.updateById(workflow);

        log.info("归档工作流成功，ID: {}", id);
    }

    @Override
    public List<WorkflowVersionResp> getVersionHistory(Long id) {
        // 检查工作流是否存在
        Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        List<WorkflowVersion> versions = workflowVersionMapper.selectByWorkflowId(id);
        return versions.stream()
                .map(this::convertToVersionResp)
                .toList();
    }

    @Override
    public WorkflowVersionResp getVersion(Long id, Integer version) {
        // 检查工作流是否存在
        Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        WorkflowVersion workflowVersion = workflowVersionMapper.selectByWorkflowIdAndVersion(id, version);
        if (workflowVersion == null) {
            throw CheckedException.notFound("版本不存在");
        }
        return convertToVersionResp(workflowVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, Integer version) {
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 检查权限
        checkOwnership(workflow);

        // 获取目标版本
        WorkflowVersion targetVersion = workflowVersionMapper.selectByWorkflowIdAndVersion(id, version);
        if (targetVersion == null) {
            throw CheckedException.notFound("目标版本不存在");
        }

        // 创建新版本（基于回滚的版本）
        int newVersion = workflow.getCurrentVersion() + 1;
        createVersion(id, newVersion, targetVersion.getGraphSnapshot(),
                "回滚到版本 " + version);

        // 更新工作流定义
        workflow.setGraph(targetVersion.getGraphSnapshot());
        workflow.setCurrentVersion(newVersion);
        // 回滚后变为草稿状态
        workflow.setStatus(WorkflowStatus.DRAFT.getCode());
        baseMapper.updateById(workflow);

        // 清理旧版本
        cleanupOldVersions(id);

        log.info("回滚工作流成功，ID: {}, 从版本 {} 回滚到版本 {}, 新版本: {}",
                id, workflow.getCurrentVersion() - 1, version, newVersion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createFromTemplate(Long templateId, String name, String description) {
        // 获取模板
        WorkflowTemplate template = Optional.ofNullable(workflowTemplateMapper.selectById(templateId))
                .orElseThrow(() -> CheckedException.notFound("模板不存在"));

        // 创建工作流
        WorkflowSaveReq req = new WorkflowSaveReq();
        req.setName(name);
        req.setDescription(description);
        req.setGraph(template.getGraph());
        req.setChangeLog("从模板创建: " + template.getName());

        return create(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long copy(Long id, String name) {
        // 获取原工作流
        WorkflowDefinition workflow = Optional.ofNullable(baseMapper.selectById(id))
                .orElseThrow(() -> CheckedException.notFound("工作流不存在"));

        // 创建新工作流
        WorkflowSaveReq req = new WorkflowSaveReq();
        req.setName(name);
        req.setDescription(workflow.getDescription());
        req.setGraph(workflow.getGraph());
        req.setInputVariables(workflow.getInputVariables());
        req.setOutputVariables(workflow.getOutputVariables());
        req.setChangeLog("复制自: " + workflow.getName());

        return create(req);
    }

    /**
     * 创建版本记录
     * @param changeLog changeLog 参数
     * @param graph graph 参数
     * @param version version 参数
     * @param workflowId 工作流标识
     */
    private void createVersion(Long workflowId, Integer version, WorkflowGraph graph, String changeLog) {
        WorkflowVersion workflowVersion = WorkflowVersion.builder()
                .workflowId(workflowId)
                .version(version)
                .graphSnapshot(graph)
                .changeLog(changeLog)
                .published(false)
                .createdBy(context.userId())
                .createdTime(Instant.now())
                .build();
        workflowVersionMapper.insert(workflowVersion);
    }

    /**
     * 清理旧版本（保留最近 MAX_VERSION_HISTORY 个）
     * @param workflowId 工作流标识
     */
    private void cleanupOldVersions(Long workflowId) {
        List<WorkflowVersion> versions = workflowVersionMapper.selectByWorkflowId(workflowId);
        if (versions.size() > MAX_VERSION_HISTORY) {
            // 删除最旧的版本（保留已发布的版本）
            versions.stream()
                    .skip(MAX_VERSION_HISTORY)
                    .filter(v -> !Boolean.TRUE.equals(v.getPublished()))
                    .forEach(v -> workflowVersionMapper.deleteById(v.getId()));
        }
    }

    /**
     * 检查图定义是否有变更
     * @param newGraph newGraph 参数
     * @param oldGraph oldGraph 参数
     * @return 处理结果
     */
    private boolean hasGraphChanged(WorkflowGraph oldGraph, WorkflowGraph newGraph) {
        if (oldGraph == null && newGraph == null) {
            return false;
        }
        if (oldGraph == null || newGraph == null) {
            return true;
        }
        // 简单比较：通过序列化后的字符串比较
        // 实际项目中可以使用更精确的比较方式
        return !oldGraph.equals(newGraph);
    }

    /**
     * 验证工作流图
     * @param graph graph 参数
     */
    private void validateGraph(WorkflowGraph graph) {
        WorkflowValidationResult result = workflowValidator.validate(graph);
        if (!result.isValid()) {
            throw CheckedException.badRequest("工作流验证失败: " + String.join("; ", result.getErrors()));
        }
        // 警告信息记录日志但不阻止保存
        if (!result.getWarnings().isEmpty()) {
            log.warn("工作流验证警告: {}", String.join("; ", result.getWarnings()));
        }
    }

    /**
     * 检查所有权
     * @param workflow 工作流
     */
    private void checkOwnership(WorkflowDefinition workflow) {
        Long currentUserId = context.userId();
        if (!workflow.getUserId().equals(currentUserId)) {
            throw CheckedException.forbidden("无权限操作此工作流");
        }
    }

    /**
     * 转换为分页响应
     * @param workflow 工作流
     * @return 处理结果
     */
    private WorkflowPageResp convertToPageResp(WorkflowDefinition workflow) {
        WorkflowPageResp resp = BeanUtil.toBean(workflow, WorkflowPageResp.class);
        // 计算节点数量
        if (workflow.getGraph() != null && workflow.getGraph().getNodes() != null) {
            resp.setNodeCount(workflow.getGraph().getNodes().size());
        } else {
            resp.setNodeCount(0);
        }
        return resp;
    }

    /**
     * 转换为详情响应
     * @param workflow 工作流
     * @return 处理结果
     */
    private WorkflowDetailResp convertToDetailResp(WorkflowDefinition workflow) {
        return BeanUtil.toBean(workflow, WorkflowDetailResp.class);
    }

    /**
     * 转换为版本响应
     * @param version version 参数
     * @return 处理结果
     */
    private WorkflowVersionResp convertToVersionResp(WorkflowVersion version) {
        return BeanUtil.toBean(version, WorkflowVersionResp.class);
    }
}
