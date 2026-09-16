package com.microservice.framework.feign.plugin;

import feign.Logger;
import feign.codec.Decoder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.openfeign.support.FeignHttpMessageConverters;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Feign 插件自动配置")
class FeignPluginConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(FeignPluginConfiguration.class);

    @Test
    @DisplayName("自定义解码器可获得 OpenFeign HTTP 消息转换器")
    void exposesFeignHttpMessageConvertersForCustomDecoder() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(Decoder.class);
            assertThat(context).hasSingleBean(FeignHttpMessageConverters.class);
        });
    }

    @Test
    @DisplayName("默认日志不输出请求头和正文")
    void defaultsToBasicLogging() {
        assertThat(new FeignPluginProperties().getLevel()).isEqualTo(Logger.Level.BASIC);
    }

    @Test
    @DisplayName("即使显式开启详细日志也过滤认证头")
    void detailedLoggerFiltersSensitiveHeaders() throws ReflectiveOperationException {
        Class<?> loggerType = Class.forName("com.microservice.framework.feign.plugin.SensitiveHeaderFilteringFeignLogger");
        Object logger = loggerType.getConstructor(Class.class).newInstance(FeignPluginConfigurationTest.class);

        Boolean authorization = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                logger, "shouldLogRequestHeader", "Authorization");
        Boolean sameToken = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                logger, "shouldLogRequestHeader", "SA-SAME-TOKEN");
        Boolean contentType = org.springframework.test.util.ReflectionTestUtils.invokeMethod(
                logger, "shouldLogRequestHeader", "Content-Type");

        Assertions.assertFalse(Boolean.TRUE.equals(authorization));
        Assertions.assertFalse(Boolean.TRUE.equals(sameToken));
        Assertions.assertTrue(Boolean.TRUE.equals(contentType));
    }
}
