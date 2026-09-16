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

package com.microservice.framework.ai.harness.spill;

import java.io.IOException;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 基于本地文件系统的 Harness Spill 默认实现。
 *
 * @author xJh
 * @since 2026-07-18
 */
public final class FileSystemHarnessSpillStore implements HarnessSpillStore {
    
    private static final Pattern SAFE_NAMESPACE = Pattern.compile("[a-zA-Z0-9_-]{1,128}");
    private static final Pattern SAFE_FILE = Pattern.compile("[a-zA-Z0-9_-]{1,128}-[0-9a-f-]{36}\\.json");
    
    private final Path root;
    private final Duration ttl;
    private final Clock clock;
    
    public FileSystemHarnessSpillStore(Path root, Duration ttl, Clock clock) {
        this.root = Objects.requireNonNull(root, "Spill 根目录不能为空").toAbsolutePath().normalize();
        this.ttl = Objects.requireNonNull(ttl, "Spill 生存时间不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Spill 生存时间必须大于零");
        }
    }
    
    @Override
    public SpillReference write(SpillWriteRequest request) {
        Objects.requireNonNull(request, "Spill 写入请求不能为空");
        String tenant = requireNamespace(request.tenantId());
        String run = requireNamespace(request.runId());
        String step = requireNamespace(request.stepId());
        Path directory = safeResolve(tenant, run);
        String fileName = step + "-" + UUID.randomUUID() + ".json";
        Path target = safeResolve(tenant, run, fileName);
        Path temporary = null;
        try {
            Files.createDirectories(directory);
            temporary = Files.createTempFile(directory, ".spill-", ".tmp");
            Files.write(temporary, request.content(), StandardOpenOption.TRUNCATE_EXISTING);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target);
            }
            Files.setLastModifiedTime(target, FileTime.from(clock.instant()));
            Instant expiresAt = clock.instant().plus(ttl);
            return new SpillReference("spill://" + tenant + "/" + run + "/" + fileName,
                    sha256(request.content()), request.content().length, expiresAt);
        } catch (IOException failure) {
            deleteQuietly(temporary);
            throw new IllegalStateException("无法写入 Harness 大结果文件", failure);
        }
    }
    
    @Override
    public Optional<byte[]> read(String tenantId, String uri) {
        String expectedTenant = requireNamespace(tenantId);
        URI reference;
        try {
            reference = URI.create(Objects.requireNonNull(uri, "Spill 引用不能为空"));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Spill 引用格式无效", failure);
        }
        if (!"spill".equals(reference.getScheme()) || reference.getHost() == null) {
            throw new IllegalArgumentException("Spill 引用协议无效");
        }
        String actualTenant = requireNamespace(reference.getHost());
        if (!expectedTenant.equals(actualTenant)) {
            throw new IllegalArgumentException("Spill 引用不属于当前租户");
        }
        String[] parts = reference.getPath().replaceFirst("^/", "").split("/");
        if (parts.length != 2 || !SAFE_FILE.matcher(parts[1]).matches()) {
            throw new IllegalArgumentException("Spill 引用路径无效");
        }
        String run = requireNamespace(parts[0]);
        Path target = safeResolve(actualTenant, run, parts[1]);
        if (!Files.isRegularFile(target)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readAllBytes(target));
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取 Harness 大结果文件", failure);
        }
    }
    
    @Override
    public int cleanupExpired() {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        Instant expiresBefore = clock.instant().minus(ttl);
        int deleted = 0;
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile)
                    .filter(this::isManagedSpillFile)
                    .toList()) {
                if (Files.getLastModifiedTime(path).toInstant().isBefore(expiresBefore)) {
                    Files.deleteIfExists(path);
                    deleted++;
                }
            }
            return deleted;
        } catch (IOException failure) {
            throw new IllegalStateException("清理过期 Harness 大结果失败", failure);
        }
    }
    
    /**
     * 只允许清理由本存储实现创建的 tenant/run/step-uuid.json 文件。
     *
     * @param path 待检查的文件路径
     * @return 是否属于本存储管理的 Spill 文件
     */
    private boolean isManagedSpillFile(Path path) {
        Path relative = root.relativize(path.toAbsolutePath().normalize());
        return relative.getNameCount() == 3
                && SAFE_NAMESPACE.matcher(relative.getName(0).toString()).matches()
                && SAFE_NAMESPACE.matcher(relative.getName(1).toString()).matches()
                && SAFE_FILE.matcher(relative.getName(2).toString()).matches();
    }
    
    private Path safeResolve(String... components) {
        Path resolved = root;
        for (String component : components) {
            resolved = resolved.resolve(component);
        }
        resolved = resolved.toAbsolutePath().normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Spill 路径越出允许目录");
        }
        return resolved;
    }
    
    private String requireNamespace(String value) {
        if (value == null || !SAFE_NAMESPACE.matcher(value).matches()) {
            throw new IllegalArgumentException("Spill 命名空间仅允许字母、数字、下划线和中划线");
        }
        return value;
    }
    
    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("当前运行环境不支持 SHA-256", failure);
        }
    }
    
    private void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // 临时文件清理失败不覆盖原始写入异常。
        }
    }
}
