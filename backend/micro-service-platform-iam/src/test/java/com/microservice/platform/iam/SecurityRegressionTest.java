package com.microservice.platform.iam;

import com.microservice.framework.db.mybatisplus.page.PageRequest;
import com.microservice.framework.security.utils.PasswordEncoderHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityRegressionTest {

    @Test
    void orderByColumnRejectsSqlInjectionPayload() {
        PageRequest request = new PageRequest();
        request.setColumn("id desc; drop table t_user");

        assertThrows(IllegalArgumentException.class, request::buildPage);
    }

    @Test
    void pageRequestHandlesNullSortDirection() {
        PageRequest request = new PageRequest();
        request.setColumn("id");
        request.setAsc(null);

        assertDoesNotThrow(() -> {
            request.buildPage();
        });
    }

    @Test
    void passwordEncoderRejectsWeakAndPlaintextFormatsForNormalMatches() {
        assertThrows(IllegalArgumentException.class, () -> PasswordEncoderHelper.encode("md5", "123456"));
        assertFalse(PasswordEncoderHelper.matches("123456", "{noop}123456"));
        assertFalse(PasswordEncoderHelper.matches("123456", "123456"));
    }
}
