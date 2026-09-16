package com.microservice.platform.suite.feign.domain.resp;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author xJh
 * @since 2025/11/6
 **/
@Data
public class OssFileResp {

    @Schema(description = "文件访问地址")
    private String url;

    @Schema(description = "文件大小，单位字节")
    private Long size;

    @Schema(description = "格式化后的文件大小")
    private String formatSize;

    @Schema(description = "文件名称")
    private String filename;

    @Schema(description = "原始文件名")
    private String originalFilename;



    /**
     * 存储路径
     */
    private String path;

    /**
     * 基础存储路径
     */
    private String basePath;

    /**
     * 文件扩展名
     */
    @TableField(value = "ext")
    private String ext;

    /**
     * MIME类型
     */
    @TableField(value = "content_type")
    private String contentType;

    /**
     * 存储平台
     */
    @TableField(value = "platform")
    private String platform;



    @Schema(description = "租户ID")
    private Long tenantId;

    @Schema(description = "对象路径（存储路径 + 文件名，不含基础路径）")
    public String getObjectPath() {
        return joinPath(path, filename);
    }

    @Schema(description = "完整存储路径（基础路径 + 存储路径 + 文件名）")
    public String getStoragePath() {
        return joinPath(basePath, getObjectPath());
    }

    private static String joinPath(String prefix, String name) {
        String left = normalizePathSegment(prefix);
        String right = normalizePathSegment(name);
        if (right == null) {
            return left;
        }
        if (left == null) {
            return right;
        }
        if (left.equals(right) || left.endsWith("/" + right)) {
            return left;
        }
        if (right.startsWith(left.endsWith("/") ? left : left + "/")) {
            return right;
        }
        return left.endsWith("/") ? left + right : left + "/" + right;
    }

    private static String normalizePathSegment(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path.trim().replace('\\', '/');
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized.isBlank() ? null : normalized;
    }

}
