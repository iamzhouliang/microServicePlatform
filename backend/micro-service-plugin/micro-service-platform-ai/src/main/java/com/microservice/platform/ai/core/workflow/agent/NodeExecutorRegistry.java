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

package com.microservice.platform.ai.core.workflow.agent;

import com.microservice.platform.ai.core.enums.NodeType;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 节点执行器注册表
 * 管理所有节点执行器的注册和获取，支持动态注册和扩展。
 *
 * @author xJh
 * @since 2026/01/07
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NodeExecutorRegistry {

    /**
     * 节点执行器映射表
     */
    private final Map<NodeType, NodeExecutor> executorMap = new ConcurrentHashMap<>();

    /**
     * 所有 NodeExecutor 实现（通过 Spring 自动注入）
     */
    private final List<NodeExecutor> nodeExecutors;

    /**
     * 初始化注册表
     */
    @PostConstruct
    public void init() {
        if (nodeExecutors == null || nodeExecutors.isEmpty()) {
            log.warn("未发现 NodeExecutor 实现");
            return;
        }

        for (NodeExecutor executor : nodeExecutors) {
            register(executor);
        }

        log.info("NodeExecutorRegistry 初始化完成，执行器数量={}，类型={}",
                executorMap.size(), executorMap.keySet());
    }

    /**
     * 注册节点执行器
     *
     * @param executor 节点执行器实现
     */
    public void register(NodeExecutor executor) {
        NodeType type = executor.getType();
        if (type == null) {
            log.warn("NodeExecutor 类型为空，跳过注册: {}",
                    executor.getClass().getSimpleName());
            return;
        }

        NodeExecutor existing = executorMap.put(type, executor);
        if (existing != null) {
            log.warn("节点类型 {} 的 NodeExecutor 被替换: {} -> {}",
                    type, existing.getClass().getSimpleName(), executor.getClass().getSimpleName());
        } else {
            log.debug("已注册节点类型 {} 的 NodeExecutor: {}",
                    type, executor.getClass().getSimpleName());
        }
    }

    /**
     * 获取节点执行器
     *
     * @param type 节点类型
     * @return 节点执行器实现
     * @throws IllegalArgumentException 如果找不到对应的执行器
     */
    public NodeExecutor getExecutor(NodeType type) {
        NodeExecutor executor = executorMap.get(type);
        if (executor == null) {
            throw new IllegalArgumentException("未注册节点类型对应的 NodeExecutor: " + type);
        }
        return executor;
    }

    /**
     * 获取节点执行器（可选）
     *
     * @param type 节点类型
     * @return 节点执行器实现，如果不存在返回 Optional.empty()
     */
    public Optional<NodeExecutor> getExecutorOptional(NodeType type) {
        return Optional.ofNullable(executorMap.get(type));
    }

    /**
     * 检查是否存在指定类型的执行器
     *
     * @param type 节点类型
     * @return 是否存在
     */
    public boolean hasExecutor(NodeType type) {
        return executorMap.containsKey(type);
    }

    /**
     * 获取所有已注册的节点类型
     *
     * @return 节点类型集合
     */
    public Set<NodeType> getRegisteredTypes() {
        return Collections.unmodifiableSet(executorMap.keySet());
    }

    /**
     * 获取所有已注册的执行器
     *
     * @return 执行器集合
     */
    public Collection<NodeExecutor> getAllExecutors() {
        return Collections.unmodifiableCollection(executorMap.values());
    }

    /**
     * 注销节点执行器
     *
     * @param type 节点类型
     * @return 被注销的执行器，如果不存在返回 null
     */
    public NodeExecutor unregister(NodeType type) {
        NodeExecutor removed = executorMap.remove(type);
        if (removed != null) {
            log.info("已注销节点类型 {} 的 NodeExecutor: {}",
                    type, removed.getClass().getSimpleName());
        }
        return removed;
    }
}
