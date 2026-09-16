package com.microservice.platform.suite.feign;

import com.microservice.framework.commons.remote.LoadService;
import com.microservice.framework.feign.plugin.token.AutoRefreshTokenProperties;
import com.microservice.platform.suite.feign.domain.resp.OssFilePreviewResp;
import com.microservice.platform.suite.feign.domain.resp.OssFileResp;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.microservice.platform.suite.feign.OssFileFeign.FEIGN_CLIENT_NAME;

/**
 * @author xJh
 * @since 2025/11/6
 **/
@FeignClient(name = FEIGN_CLIENT_NAME, dismiss404 = true, path = "/oss/files")
public interface OssFileFeign extends LoadService<OssFilePreviewResp> {

    String FEIGN_CLIENT_NAME = "micro-service-platform-suite";

    /**
     * 文件上传接口
     *
     * @param multipartFile 文件
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, headers = {AutoRefreshTokenProperties.X_AUTO_TOKEN, "ignore-header=Content-Type"})
    OssFileResp upload(@RequestPart("file") MultipartFile multipartFile);

    /**
     * 按对象路径上传文件.
     *
     * @param objectPath 对象路径
     * @param multipartFile 文件
     */
    @PostMapping(value = "/objects/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, headers = {AutoRefreshTokenProperties.X_AUTO_TOKEN, "ignore-header=Content-Type"})
    void uploadObject(@RequestParam("objectPath") String objectPath, @RequestPart("file") MultipartFile multipartFile);

    /**
     * 按对象路径读取文本文件.
     *
     * @param objectPath 对象路径
     * @return 文本内容
     */
    @GetMapping("/objects/text")
    String readText(@RequestParam("objectPath") String objectPath);

    /**
     * 按对象路径写入文本文件.
     *
     * @param objectPath 对象路径
     * @param content 文本内容
     */
    @PutMapping(value = "/objects/text", consumes = MediaType.TEXT_PLAIN_VALUE)
    void writeText(@RequestParam("objectPath") String objectPath, @RequestBody String content);

    /**
     * 按文件夹前缀列出对象.
     *
     * @param folderPath 文件夹前缀
     * @return 对象路径列表
     */
    @GetMapping("/objects")
    List<String> listObjects(@RequestParam("folderPath") String folderPath);

    /**
     * 按文件夹前缀下载 zip.
     *
     * @param folderPath 文件夹前缀
     * @return zip 文件字节
     */
    @GetMapping(value = "/folders/download", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    byte[] downloadFolder(@RequestParam("folderPath") String folderPath);

    /**
     * 按文件夹前缀删除对象.
     *
     * @param folderPath 文件夹前缀
     */
    @DeleteMapping("/folders")
    void deleteFolder(@RequestParam("folderPath") String folderPath);

    /**
     * 根据 ID 批量查询
     *
     * @param ids 唯一键（可能不是主键ID)
     * @return 查询结果
     */
    @Override
    @PostMapping("/preview-map")
    Map<Object, OssFilePreviewResp> findByIds(@RequestBody Set<Object> ids);

    /**
     * 根据原始的url 列表 返回一个预签名可下载的 oss url 列表
     * @param req req
     * @return 可预览的地址
     */
    @PostMapping("/preview-list")
    List<String> previewList(@RequestBody List<String> req);

    /**
     * 根据一个原始的url列表 返回一个 map
     * map 的 key 是原始的url value 是预签名的oss url
     * @param req req
     * @return 可预览的地址
     */
    @PostMapping("/preview-map")
    Map<String, String> previewMap(@RequestBody List<String> req);

}
