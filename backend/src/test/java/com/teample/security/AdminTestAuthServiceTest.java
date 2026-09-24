package com.teample.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;

class AdminTestAuthServiceTest {
    @Test void productionRejectsAdminEvenWhenExplicitlyEnabled() {
        AdminTestAuthService service = new AdminTestAuthService();
        MockEnvironment environment = new MockEnvironment(); environment.setActiveProfiles("prod", "dev");
        ReflectionTestUtils.setField(service, "environment", environment);
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "adminId", "local-admin");
        ReflectionTestUtils.setField(service, "adminPassword", "long-local-password");
        assertThat(service.matches("local-admin", "long-local-password")).isFalse();
        environment.setActiveProfiles("dev");
        assertThat(service.matches("local-admin", "long-local-password")).isTrue();
        ReflectionTestUtils.setField(service, "adminPassword", "1234");
        assertThat(service.matches("local-admin", "1234")).isFalse();
    }
    @Test void defaultsDoNotPermitTestCredentials() {
        assertThat(new AdminTestAuthService().matches("admin", "1234")).isFalse();
    }
}
