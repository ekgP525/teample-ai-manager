package com.teample.security;

import com.teample.controller.AuthController;
import com.teample.dto.auth.AdminLoginRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AdminTestAuthServiceTest {

    private static final String STRONG = "correct-horse-battery";

    @Test
    void disabledByDefaultEvenWithCredentials() {
        AdminTestAuthService service = new AdminTestAuthService(false, "admin", STRONG);

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.matches("admin", STRONG)).isFalse();
    }

    @Test
    void requiresNonBlankIdAndTwelveCharacterPassword() {
        assertThat(new AdminTestAuthService(true, "", STRONG).matches("", STRONG)).isFalse();
        assertThat(new AdminTestAuthService(true, "admin", "1234").matches("admin", "1234")).isFalse();
        assertThat(new AdminTestAuthService(true, "admin", "short-pass1").isEnabled()).isFalse();

        AdminTestAuthService service = new AdminTestAuthService(true, "admin", STRONG);
        assertThat(service.isEnabled()).isTrue();
        assertThat(service.matches("admin", STRONG)).isTrue();
        assertThat(service.matches("admin", "wrong-wrong-wrong")).isFalse();
        assertThat(service.matches(null, STRONG)).isFalse();
    }

    @Test
    void verifyEndpointReturns404WhenDisabledAnd429AfterFiveFailures() {
        AuthController disabled = new AuthController(new AdminTestAuthService(false, "admin", STRONG));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");

        assertThatThrownBy(() -> disabled.verifyAdmin(new AdminLoginRequest("admin", STRONG), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");

        AdminTestAuthService service = new AdminTestAuthService(true, "admin", STRONG);
        AuthController controller = new AuthController(service);
        for (int attempt = 0; attempt < AdminTestAuthService.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThatThrownBy(() -> controller.verifyAdmin(new AdminLoginRequest("admin", "nope-nope-nope"), request))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("401");
        }
        assertThat(service.isRateLimited("10.0.0.1")).isTrue();
        assertThatThrownBy(() -> controller.verifyAdmin(new AdminLoginRequest("admin", STRONG), request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("429");

        MockHttpServletRequest other = new MockHttpServletRequest();
        other.setRemoteAddr("10.0.0.2");
        assertThat(controller.verifyAdmin(new AdminLoginRequest("admin", STRONG), other).getBody().admin()).isTrue();
        assertThat(service.isRateLimited("10.0.0.2")).isFalse();
    }
}
