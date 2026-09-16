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

package com.microservice.platform.ai.core.workflow.agent.impl;

import com.microservice.platform.ai.core.enums.NodeType;
import com.microservice.platform.ai.core.workflow.agent.AbstractNodeExecutor;
import com.microservice.platform.ai.core.workflow.agent.NodeExecutionResult;
import com.microservice.platform.ai.core.workflow.config.node.DocExtractorConfig;
import com.microservice.platform.ai.core.workflow.config.node.DocExtractorConfig.DocumentType;
import com.microservice.platform.ai.core.workflow.config.node.DocExtractorConfig.ValidationResult;
import com.microservice.platform.ai.core.workflow.context.ExecutionContext;
import com.microservice.platform.ai.domain.entity.workflow.WorkflowNode;
import com.microservice.platform.ai.service.WorkflowFileService;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * 文档提取器节点执行器
 * 从文档中提取文本 (PDF、Word、Excel、PPT 等)
 * 使用 Apache Tika 进行文档解析
 * 功能特性:
 * - 支持 PDF 文本提取
 * - 支持 Word 文档提取 (DOC, DOCX)
 * - 支持 Excel 表格提取 (XLS, XLSX)
 * - 支持 PowerPoint 提取 (PPT, PPTX)
 * - 支持纯文本和 Markdown 文件
 * - 支持元数据提取
 *
 * @author xJh
 * @since 2026/01/08
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocExtractorNodeExecutor extends AbstractNodeExecutor {

    /**
     * Langchain4j 文档解析器（封装了 Apache Tika）
     */
    private static final DocumentParser DOCUMENT_PARSER = new ApacheTikaDocumentParser();

    /**
     * 工作流文件服务
     */
    private final WorkflowFileService workflowFileService;

    /**
     * 文档提取器（用于测试注入）
     */
    @Setter
    private DocumentExtractor documentExtractor;

    /**
     * 文档提取器接口
     */
    @FunctionalInterface
    public interface DocumentExtractor {

        ExtractionResult extract(Object fileInput, DocExtractorConfig config) throws Exception;
    }

    /**
     * 提取结果
     * @param metadata metadata 参数
     * @param pageCount pageCount 参数
     * @param text 文本内容
     * @return 处理结果
     * @throws Exception 处理失败时抛出
     */
    public record ExtractionResult(
                                   String text,
                                   Map<String, Object> metadata,
                                   int pageCount) {
    }

    @Override
    public NodeType getType() {
        return NodeType.DOC_EXTRACTOR;
    }

    @Override
    public void validate(WorkflowNode node) {
        DocExtractorConfig config = parseConfig(node, DocExtractorConfig.class);

        // 使用强类型配置验证
        ValidationResult validationResult = config.validate();
        if (!validationResult.isValid()) {
            throw new IllegalArgumentException(
                    "Doc extractor node configuration invalid: " + String.join(", ", validationResult.getErrors()));
        }
    }

    @Override
    protected NodeExecutionResult doExecute(WorkflowNode node, ExecutionContext context) {
        log.debug("开始执行 DOC_EXTRACTOR 节点: {}", node.getId());

        // 解析强类型配置
        DocExtractorConfig config = parseConfig(node, DocExtractorConfig.class);

        // 获取文件变量
        String fileVariable = config.getFileVariable();
        if (fileVariable == null || fileVariable.trim().isEmpty()) {
            return NodeExecutionResult.failure("未配置文件变量");
        }

        // 解析变量引用
        String varPath = fileVariable.trim();
        if (varPath.startsWith("{{") && varPath.endsWith("}}")) {
            varPath = varPath.substring(2, varPath.length() - 2).trim();
        }

        Object fileInput = context.getVariable(varPath, Object.class);
        if (fileInput == null) {
            return NodeExecutionResult.failure("文件输入为空，变量: " + varPath);
        }

        try {
            // 提取文档内容
            ExtractionResult extractionResult;
            if (documentExtractor != null) {
                // 使用注入的提取器（测试用）
                extractionResult = documentExtractor.extract(fileInput, config);
            } else {
                // 使用默认提取器
                extractionResult = extractDocument(fileInput, config);
            }

            // 构建输出
            Map<String, Object> outputs = new HashMap<>();
            outputs.put("result", extractionResult.text());
            outputs.put("text", extractionResult.text());
            outputs.put("pageCount", extractionResult.pageCount());

            if (config.isExtractMetadata() && extractionResult.metadata() != null) {
                outputs.put("metadata", extractionResult.metadata());
            }

            // 设置配置的输出变量
            String outputVariable = config.getOutputVariable();
            if (outputVariable != null && !outputVariable.isEmpty()) {
                outputs.put(outputVariable, extractionResult.text());
            }

            log.debug("DOC_EXTRACTOR 节点 {} 执行成功，提取字符数={}",
                    node.getId(), extractionResult.text().length());
            return NodeExecutionResult.success(outputs);

        } catch (Exception e) {
            log.error("文档提取节点 {} 执行失败: {}", node.getId(), e.getMessage(), e);
            return NodeExecutionResult.failure("文档提取失败: " + e.getMessage());
        }
    }

    /**
     * 提取文档内容
     * @param config 节点配置
     * @param fileInput fileInput 参数
     * @return 处理结果
     * @throws IllegalArgumentException 参数不合法时抛出
     * @throws Exception 处理失败时抛出
     */
    private ExtractionResult extractDocument(Object fileInput, DocExtractorConfig config) throws Exception {
        // 确定文件类型和内容
        byte[] fileContent;
        String fileName;
        DocumentType documentType;

        if (fileInput instanceof byte[] bytes) {
            fileContent = bytes;
            fileName = "unknown";
            documentType = detectDocumentType(bytes);
        } else if (fileInput instanceof String str) {
            // 检查是否是文件ID（以 wf_ 开头）
            if (str.startsWith("wf_")) {
                fileContent = workflowFileService.getFileContent(str);
                Map<String, Object> fileInfo = workflowFileService.getFileInfo(str);
                fileName = (String) fileInfo.getOrDefault("name", "unknown");
                documentType = DocumentType.fromExtension(getFileExtension(fileName));
            } else if (looksLikeFilePath(str)) {
                // 安全：禁止按本地文件系统路径读取，防止任意文件读取（LFI，如 /etc/passwd）。
                // 文件输入必须使用受控的 wf_ 文件 ID 或 Base64 内容。
                throw new IllegalArgumentException("不支持按文件路径读取，请使用文件ID（wf_ 前缀）或 Base64 内容");
            } else {
                // 假设是 Base64 编码
                fileContent = Base64.getDecoder().decode(str);
                fileName = "unknown";
                documentType = detectDocumentType(fileContent);
            }
        } else if (fileInput instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fileMap = (Map<String, Object>) fileInput;

            // 优先使用 fileId
            String fileId = (String) fileMap.get("fileId");
            if (fileId != null && fileId.startsWith("wf_")) {
                fileContent = workflowFileService.getFileContent(fileId);
                Map<String, Object> fileInfo = workflowFileService.getFileInfo(fileId);
                fileName = (String) fileInfo.getOrDefault("name", "unknown");
            } else {
                fileName = (String) fileMap.getOrDefault("name", "unknown");
                Object content = fileMap.get("content");
                if (content instanceof byte[] bytes) {
                    fileContent = bytes;
                } else if (content instanceof String str) {
                    fileContent = Base64.getDecoder().decode(str);
                } else {
                    throw new IllegalArgumentException("不支持的文件内容类型");
                }
            }
            documentType = DocumentType.fromExtension(getFileExtension(fileName));
        } else {
            throw new IllegalArgumentException("不支持的文件输入类型: " + fileInput.getClass().getName());
        }

        // 检查文件大小
        long maxSize = config.getEffectiveMaxFileSize();
        if (fileContent.length > maxSize) {
            throw new IllegalArgumentException(
                    String.format("File size %d exceeds max limit %d", fileContent.length, maxSize));
        }

        // 检查文件类型是否支持
        if (documentType == null) {
            // 默认作为文本处理
            documentType = DocumentType.TXT;
        }

        if (!config.isTypeSupported(documentType.getExtension())) {
            throw new IllegalArgumentException("不支持的文档类型: " + documentType);
        }

        // 使用 Apache Tika 提取内容
        return extractWithTika(fileContent, config);
    }

    /**
     * 使用 Langchain4j ApacheTikaDocumentParser 提取文档内容
     * @param config 节点配置
     * @param content 内容
     * @return 处理结果
     * @throws Exception 处理失败时抛出
     */
    private ExtractionResult extractWithTika(byte[] content, DocExtractorConfig config) throws Exception {
        // 使用 langchain4j 封装的 Tika 解析器
        try (InputStream stream = new ByteArrayInputStream(content)) {
            Document document = DOCUMENT_PARSER.parse(stream);
            String text = document.text();

            // 提取元数据
            Map<String, Object> metadataMap = new HashMap<>();
            if (config.isExtractMetadata() && document.metadata() != null) {
                document.metadata().toMap().forEach((key, value) -> {
                    if (value != null) {
                        metadataMap.put(key, value);
                    }
                });
            }

            // 页数（langchain4j 不直接提供，默认为1）
            int pageCount = 1;

            return new ExtractionResult(text, metadataMap, pageCount);
        }
    }

    /**
     * 根据文件名后缀检测文档类型
     * @param content 内容
     * @return 处理结果
     */
    private DocumentType detectDocumentType(byte[] content) {
        // 简化检测逻辑，依赖文件名后缀
        return DocumentType.TXT;
    }

    /**
     * 判断输入是否形似本地文件路径（用于拒绝，而非读取）。
     * @param str str 参数
     * @return 处理结果
     */
    private boolean looksLikeFilePath(String str) {
        return str.contains("/") || str.contains("\\") || str.endsWith(".pdf")
                || str.endsWith(".docx") || str.endsWith(".xlsx") || str.endsWith(".txt");
    }

    /**
     * 获取文件扩展名
     * @param fileName fileName 参数
     * @return 处理结果
     */
    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot) : "";
    }
}
