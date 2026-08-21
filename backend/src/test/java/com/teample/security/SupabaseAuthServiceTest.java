package com.teample.security;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseAuthServiceTest {

    private static final String SECRET = "test-supabase-jwt-secret";

    @Test
    void authenticatesValidSupabaseJwt() {
        SupabaseAuthService service = serviceWithSecret(SECRET);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "exp": %d,
                  "user_metadata": {
                    "display_name": "김민재"
                  }
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()), SECRET);

        AuthenticatedUser user = service.authenticate(requestWithBearer(token));

        assertThat(user.authUserId()).isEqualTo("supabase-user-1");
        assertThat(user.email()).isEqualTo("minjae@example.com");
        assertThat(user.memberKey()).isEqualTo("김민재");
    }

    @Test
    void usesEmailPrefixWhenMetadataNameIsMissing() {
        SupabaseAuthService service = serviceWithSecret(SECRET);
        String token = token("""
                {
                  "sub": "supabase-user-2",
                  "email": "owner@example.com",
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()), SECRET);

        AuthenticatedUser user = service.authenticate(requestWithBearer(token));

        assertThat(user.memberKey()).isEqualTo("owner");
    }

    @Test
    void rejectsExpiredJwt() {
        SupabaseAuthService service = serviceWithSecret(SECRET);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "exp": %d
                }
                """.formatted(Instant.now().minusSeconds(1).getEpochSecond()), SECRET);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsInvalidSignature() {
        SupabaseAuthService service = serviceWithSecret(SECRET);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()), "wrong-secret");

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("Invalid Supabase access token");
    }

    @Test
    void rejectsMissingJwtSecret() {
        SupabaseAuthService service = serviceWithSecret("");
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()), SECRET);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("auth settings are missing");
    }

    private SupabaseAuthService serviceWithSecret(String secret) {
        SupabaseAuthService service = new SupabaseAuthService();
        ReflectionTestUtils.setField(service, "supabaseJwtSecret", secret);
        return service;
    }

    private HttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private String token(String payloadJson, String secret) {
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url(payloadJson);
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken, secret);
    }

    private String sign(String value, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
