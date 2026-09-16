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

package com.microservice.platform.ai.core.workflow.config.node;

import com.microservice.platform.ai.core.enums.AiEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档提取器节点配置 ( Doc Extractor)
 * 从文档中提取文本 (PDF、Word、Excel、PPT 等)
 *
 * @author xJh
 * @since 2026/01/07
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocExtractorConfig {

    /**
     * 默认最大文件大小（50MB）
     */
    public static final long DEFAULT_MAX_FILE_SIZE = 50 * 1024 * 1024L;

    /**
     * 默认支持的文档类型
     */
    public static final List<DocumentType> DEFAULT_SUPPORTED_TYPES = Arrays.asList(
            DocumentType.PDF, DocumentType.DOCX, DocumentType.DOC,
            DocumentType.XLSX, DocumentType.XLS, DocumentType.PPTX, DocumentType.PPT,
            DocumentType.TXT, DocumentType.CSV, DocumentType.MD);

    /**
     * 变量引用正则表达式
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{([^}]+)\\}\\}");

    /**
     * 文件变量
     * 支持变量引用: {{nodeName.fileVariable}}
     */
    private String fileVariable;

    /**
     * 支持的文档类型
     */
    private List<DocumentType> supportedTypes;

    /**
     * 输出变量名
     */
    private String outputVariable;

    /**
     * 是否提取元数据
     */
    private boolean extractMetadata;

    /**
     * 是否保留格式
     */
    private boolean preserveFormatting;

    /**
     * 最大文件大小（字节）
     */
    private Long maxFileSize;

    /**
     * OCR 配置（用于图片和扫描 PDF）
     */
    private OcrConfig ocrConfig;

    /**
     * 分页配置
     */
    private PaginationConfig paginationConfig;

    /**
     * 文档类型枚举
     */
    @Getter
    @AllArgsConstructor
    public enum DocumentType implements AiEnum {

        PDF("PDF", "PDF 文档", ".pdf"),
        DOCX("DOCX", "Word 文档", ".docx"),
        DOC("DOC", "Word 97-2003 文档", ".doc"),
        XLSX("XLSX", "Excel 表格", ".xlsx"),
        XLS("XLS", "Excel 97-2003 表格", ".xls"),
        PPTX("PPTX", "PowerPoint 演示文稿", ".pptx"),
        PPT("PPT", "PowerPoint 97-2003 演示文稿", ".ppt"),
        TXT("TXT", "纯文本", ".txt"),
        CSV("CSV", "CSV 文件", ".csv"),
        MD("MD", "Markdown 文档", ".md"),
        HTML("HTML", "HTML 文档", ".html"),
        RTF("RTF", "富文本格式", ".rtf"),
        EPUB("EPUB", "电子书", ".epub");

        private final String code;
        private final String description;
        private final String extension;

        /**
         * 根据文件扩展名获取文档类型
         *
         * @param extension 文件扩展名（包含点号）
         * @return 文档类型，如果不支持则返回 null
         */
        public static DocumentType fromExtension(String extension) {
            if (extension == null) {
                return null;
            }
            String ext = extension.toLowerCase();
            for (DocumentType type : values()) {
                if (type.getExtension().equalsIgnoreCase(ext)) {
                    return type;
                }
            }
            return null;
        }
    }

    /**
     * OCR 配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OcrConfig {

        /**
         * 是否启用 OCR
         */
        private boolean enabled;

        /**
         * OCR 语言
         * 如: chi_sim (简体中文), eng (英文), chi_sim+eng (中英混合)
         */
        private String language;

        /**
         * OCR 引擎
         */
        private OcrEngine engine;

        /**
         * 图像预处理
         */
        private boolean preprocessImage;

        /**
         * DPI 设置（用于 PDF 转图像）
         */
        private Integer dpi;
    }

    /**
     * OCR 引擎枚举
     */
    @Getter
    @AllArgsConstructor
    public enum OcrEngine implements AiEnum {

        TESSERACT("TESSERACT", "Tesseract OCR"),
        PADDLE_OCR("PADDLE_OCR", "PaddleOCR"),
        CLOUD_OCR("CLOUD_OCR", "云端 OCR 服务");

        private final String code;
        private final String description;
    }

    /**
     * 分页配置
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationConfig {

        /**
         * 是否按页分割
         */
        private boolean splitByPage;

        /**
         * 起始页码（从1开始）
         */
        private Integer startPage;

        /**
         * 结束页码
         */
        private Integer endPage;

        /**
         * 页面分隔符
         */
        private String pageSeparator;
    }

    /**
     * 验证结果
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {

        private boolean valid;
        private List<String> errors;

        public static ValidationResult success() {
            return new ValidationResult(true, new ArrayList<>());
        }

        public static ValidationResult failure(List<String> errors) {
            return new ValidationResult(false, errors);
        }
    }

    /**
     * 验证配置
     *
     * @return 验证结果
     */
    public ValidationResult validate() {
        List<String> errors = new ArrayList<>();

        // 验证文件变量
        if (fileVariable == null || fileVariable.trim().isEmpty()) {
            errors.add("文件变量不能为空");
        }

        // 验证输出变量名
        if (outputVariable == null || outputVariable.trim().isEmpty()) {
            errors.add("输出变量名不能为空");
        }

        // 验证最大文件大小
        if (maxFileSize != null && maxFileSize <= 0) {
            errors.add("最大文件大小必须大于0");
        }

        // 验证分页配置
        if (paginationConfig != null) {
            if (paginationConfig.getStartPage() != null && paginationConfig.getStartPage() < 1) {
                errors.add("起始页码必须大于等于1");
            }
            if (paginationConfig.getEndPage() != null && paginationConfig.getEndPage() < 1) {
                errors.add("结束页码必须大于等于1");
            }
            if (paginationConfig.getStartPage() != null && paginationConfig.getEndPage() != null
                    && paginationConfig.getStartPage() > paginationConfig.getEndPage()) {
                errors.add("起始页码不能大于结束页码");
            }
        }

        // 验证 OCR 配置
        if (ocrConfig != null && ocrConfig.isEnabled()) {
            if (ocrConfig.getEngine() == null) {
                errors.add("启用 OCR 时必须指定 OCR 引擎");
            }
            if (ocrConfig.getDpi() != null && ocrConfig.getDpi() < 72) {
                errors.add("DPI 设置不能小于 72");
            }
        }

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 获取依赖的变量引用
     *
     * @return 依赖的变量引用列表
     */
    public List<String> getDependentVariables() {
        List<String> variables = new ArrayList<>();
        if (fileVariable != null) {
            Matcher matcher = VARIABLE_PATTERN.matcher(fileVariable);
            while (matcher.find()) {
                variables.add(matcher.group(1).trim());
            }
        }
        return variables;
    }

    /**
     * 获取依赖的节点ID
     *
     * @return 依赖的节点ID集合
     */
    public Set<String> getDependentNodeIds() {
        Set<String> nodeIds = new HashSet<>();
        for (String varPath : getDependentVariables()) {
            String[] parts = varPath.split("\\.", 2);
            if (parts.length > 0) {
                nodeIds.add(parts[0]);
            }
        }
        return nodeIds;
    }

    /**
     * 检查文件类型是否支持
     *
     * @param extension 文件扩展名
     * @return 是否支持
     */
    public boolean isTypeSupported(String extension) {
        DocumentType type = DocumentType.fromExtension(extension);
        if (type == null) {
            return false;
        }

        List<DocumentType> types = supportedTypes != null ? supportedTypes : DEFAULT_SUPPORTED_TYPES;
        return types.contains(type);
    }

    /**
     * 获取有效的最大文件大小
     *
     * @return 最大文件大小（字节）
     */
    public long getEffectiveMaxFileSize() {
        return maxFileSize != null && maxFileSize > 0 ? maxFileSize : DEFAULT_MAX_FILE_SIZE;
    }
}
