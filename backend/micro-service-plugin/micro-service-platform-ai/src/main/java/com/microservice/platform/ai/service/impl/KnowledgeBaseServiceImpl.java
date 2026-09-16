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
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.platform.ai.core.enums.KnowledgeItemType;
import com.microservice.platform.ai.domain.dto.req.KnowledgeBasePageReq;
import com.microservice.platform.ai.domain.dto.req.KnowledgeBaseSaveReq;
import com.microservice.platform.ai.domain.dto.resp.KnowledgeBaseResp;
import com.microservice.platform.ai.domain.entity.KnowledgeBase;
import com.microservice.platform.ai.domain.entity.KnowledgeItem;
import com.microservice.platform.ai.repository.KnowledgeBaseMapper;
import com.microservice.platform.ai.repository.KnowledgeItemMapper;
import com.microservice.platform.ai.service.GraphService;
import com.microservice.platform.ai.service.KnowledgeBaseService;
import com.microservice.platform.ai.service.KnowledgeChunkService;
import com.microservice.platform.ai.service.VectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 知识库服务实现类
 *
 * @author xJh
 * @since 2025/10/20
 **/
@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl extends SuperServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    private final KnowledgeItemMapper knowledgeItemMapper;
    private final KnowledgeChunkService knowledgeChunkService;
    // 用 ObjectProvider 延迟解析，避免与 GraphServiceImpl / VectorServiceImpl(均注入 KnowledgeBaseService) 形成构造期循环依赖
    private final ObjectProvider<GraphService> graphServiceProvider;
    private final ObjectProvider<VectorService> vectorServiceProvider;

    @Override
    public IPage<KnowledgeBaseResp> pageList(KnowledgeBasePageReq req) {
        IPage<KnowledgeBase> page = baseMapper.selectPage(req.buildPage(), Wraps.<KnowledgeBase>lbQ()
                .like(KnowledgeBase::getName, req.getName())
                .orderByDesc(KnowledgeBase::getCreateTime));
        Map<Long, Map<String, Long>> countMap = loadTypeCounts(page.getRecords());
        return page.convert(kb -> convertToResp(kb, countMap));
    }

    @Override
    public List<KnowledgeBaseResp> listAll() {
        List<KnowledgeBase> bases = baseMapper.selectList(Wraps.<KnowledgeBase>lbQ()
                .orderByDesc(KnowledgeBase::getCreateTime));
        Map<Long, Map<String, Long>> countMap = loadTypeCounts(bases);
        return bases.stream().map(kb -> convertToResp(kb, countMap)).toList();
    }

    @Override
    public KnowledgeBaseResp detail(Long id) {
        var entity = Optional.ofNullable(baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("知识库不存在"));
        return convertToResp(entity, loadTypeCounts(List.of(entity)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(KnowledgeBaseSaveReq req) {
        KnowledgeBase knowledgeBase = BeanUtil.toBean(req, KnowledgeBase.class);
        knowledgeBase.setVersion(1);
        // deleted 由 @TableLogic + FieldFill(INSERT) 及 DB 默认值自动填充，无需手动置 false
        save(knowledgeBase);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void modify(Long id, KnowledgeBaseSaveReq req) {
        Optional.ofNullable(baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("知识库不存在"));
        // 存在性校验后按 id + 请求字段构建实体执行 updateById。
        // 注意：此处未回填原 version，本次更新不强制乐观锁；如需乐观锁应改为在已查出实体上复制字段后再更新。
        var entity = BeanUtilPlus.toBean(id, req, KnowledgeBase.class);
        baseMapper.updateById(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Optional.ofNullable(baseMapper.selectById(id)).orElseThrow(() -> CheckedException.notFound("知识库不存在"));
        // 级联清理，保证 知识库 ↔ 条目 ↔ 分片 ↔ 向量 ↔ 图谱 一致，避免孤儿数据
        // 1. 物理向量 + 向量元数据（deleteVectorsByKbId 内部会一并删除向量元数据）
        int deletedVectors = vectorServiceProvider.getObject().deleteVectorsByKbId(id);
        log.info("删除知识库 {} 关联的向量 {} 条", id, deletedVectors);
        // 2. 图谱数据（图谱未启用时内部跳过），避免 Neo4j 图谱节点残留
        graphServiceProvider.ifAvailable(graphService -> graphService.deleteGraphForKnowledgeBase(id));
        // 3. 知识分片
        knowledgeChunkService.deleteByKbId(id);
        // 4. 知识条目
        int deletedItems = knowledgeItemMapper.delete(Wraps.<KnowledgeItem>lbQ().eq(KnowledgeItem::getKbId, id));
        log.info("删除知识库 {} 关联的知识条目 {} 条", id, deletedItems);
        // 5. 知识库
        baseMapper.deleteById(id);
    }

    /**
     * 将实体转换为响应对象
     *
     * @param knowledgeBase 知识库实体
     * @param countMap countMap 参数
     * @return 知识库响应对象
     */
    private KnowledgeBaseResp convertToResp(KnowledgeBase knowledgeBase, Map<Long, Map<String, Long>> countMap) {
        KnowledgeBaseResp resp = BeanUtil.toBean(knowledgeBase, KnowledgeBaseResp.class);
        Map<String, Long> counts = countMap.getOrDefault(knowledgeBase.getId(), Map.of());
        resp.setDocumentCount(counts.getOrDefault(KnowledgeItemType.DOCUMENT.getValue(), 0L));
        resp.setFaqCount(counts.getOrDefault(KnowledgeItemType.QA_PAIR.getValue(), 0L));
        resp.setStructuredCount(counts.getOrDefault(KnowledgeItemType.STRUCTURED.getValue(), 0L));
        resp.setItemCount(counts.values().stream().mapToLong(Long::longValue).sum());
        return resp;
    }

    /**
     * 批量统计给定知识库的各类型条目数量：一次分组查询后按 kbId→(type→count) 回填，避免逐条 N+1。
     * 逻辑删除由 @TableLogic 自动追加 deleted=0，租户范围由多租户插件自动追加。
     * @param bases bases 参数
     * @return 处理结果
     */
    private Map<Long, Map<String, Long>> loadTypeCounts(Collection<KnowledgeBase> bases) {
        List<Long> kbIds = bases.stream().map(KnowledgeBase::getId).filter(Objects::nonNull).distinct().toList();
        if (kbIds.isEmpty()) {
            return Map.of();
        }
        QueryWrapper<KnowledgeItem> qw = new QueryWrapper<>();
        qw.select("kb_id", "type", "count(*) AS cnt")
                .in("kb_id", kbIds)
                .groupBy("kb_id", "type")
                .orderByAsc("kb_id", "type");
        Map<Long, Map<String, Long>> result = new HashMap<>();
        for (Map<String, Object> row : knowledgeItemMapper.selectMaps(qw)) {
            Long kbId = ((Number) row.get("kb_id")).longValue();
            String type = String.valueOf(row.get("type"));
            long cnt = ((Number) row.get("cnt")).longValue();
            result.computeIfAbsent(kbId, k -> new HashMap<>()).put(type, cnt);
        }
        return result;
    }
}
