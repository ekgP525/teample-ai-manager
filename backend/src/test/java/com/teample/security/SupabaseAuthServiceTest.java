package com.teample.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SupabaseAuthServiceTest {

    private static final String KEY_ID = "test-key-1";
    private static final String JWKS_URI = "https://project-ref.supabase.co/auth/v1/.well-known/jwks.json";
    private static final String ISSUER = "https://project-ref.supabase.co/auth/v1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void authenticatesValidSupabaseJwt() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "iss": "%s",
                  "exp": %d,
                  "user_metadata": {
                    "display_name": "Minjae"
                  }
                }
                """.formatted(ISSUER, Instant.now().plusSeconds(3600).getEpochSecond()), keyPair, KEY_ID);

        AuthenticatedUser user = service.authenticate(requestWithBearer(token));

        assertThat(user.authUserId()).isEqualTo("supabase-user-1");
        assertThat(user.email()).isEqualTo("minjae@example.com");
        assertThat(user.memberKey()).isEqualTo("Minjae");
    }

    @Test
    void usesEmailPrefixWhenMetadataNameIsMissing() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        String token = token("""
                {
                  "sub": "supabase-user-2",
                  "email": "owner@example.com",
                  "iss": "%s",
                  "exp": %d
                }
                """.formatted(ISSUER, Instant.now().plusSeconds(3600).getEpochSecond()), keyPair, KEY_ID);

        AuthenticatedUser user = service.authenticate(requestWithBearer(token));

        assertThat(user.memberKey()).isEqualTo("owner");
    }

    @Test
    void rejectsExpiredJwt() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "iss": "%s",
                  "exp": %d
                }
                """.formatted(ISSUER, Instant.now().minusSeconds(1).getEpochSecond()), keyPair, KEY_ID);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void rejectsInvalidSignature() throws Exception {
        KeyPair trustedKeyPair = keyPair();
        KeyPair signingKeyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(trustedKeyPair), ISSUER);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "iss": "%s",
                  "exp": %d
                }
                """.formatted(ISSUER, Instant.now().plusSeconds(3600).getEpochSecond()), signingKeyPair, KEY_ID);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("Invalid Supabase access token");
    }

    @Test
    void rejectsUnsupportedLegacyHs256Jwt() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        String token = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}")
                + "."
                + base64Url("{\"sub\":\"supabase-user-1\",\"exp\":" + Instant.now().plusSeconds(3600).getEpochSecond() + "}")
                + ".signature";

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("Unsupported Supabase access token algorithm");
    }

    @Test
    void rejectsInvalidIssuer() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "iss": "https://other.supabase.co/auth/v1",
                  "exp": %d
                }
                """.formatted(Instant.now().plusSeconds(3600).getEpochSecond()), keyPair, KEY_ID);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("issuer");
    }

    @Test
    void rejectsMissingJwksUri() throws Exception {
        KeyPair keyPair = keyPair();
        SupabaseAuthService service = serviceWithJwks(jwks(keyPair), ISSUER);
        ReflectionTestUtils.setField(service, "supabaseJwksUri", "");
        String token = token("""
                {
                  "sub": "supabase-user-1",
                  "email": "minjae@example.com",
                  "iss": "%s",
                  "exp": %d
                }
                """.formatted(ISSUER, Instant.now().plusSeconds(3600).getEpochSecond()), keyPair, KEY_ID);

        assertThatThrownBy(() -> service.authenticate(requestWithBearer(token)))
                .isInstanceOf(AuthRequiredException.class)
                .hasMessageContaining("JWKS settings are missing");
    }

    private SupabaseAuthService serviceWithJwks(JsonNode jwks, String issuer) {
        SupabaseAuthService service = new TestSupabaseAuthService(jwks);
        ReflectionTestUtils.setField(service, "supabaseJwksUri", JWKS_URI);
        ReflectionTestUtils.setField(service, "supabaseJwtIssuer", issuer);
        return service;
    }

    private HttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private KeyPair keyPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        return generator.generateKeyPair();
    }

    private JsonNode jwks(KeyPair keyPair) throws Exception {
        ECPublicKey publicKey = (ECPublicKey) keyPair.getPublic();
        String x = base64Url(unsignedFixed(publicKey.getW().getAffineX(), 32));
        String y = base64Url(unsignedFixed(publicKey.getW().getAffineY(), 32));
        return OBJECT_MAPPER.readTree("""
                {
                  "keys": [
                    {
                      "kty": "EC",
                      "kid": "%s",
                      "alg": "ES256",
                      "crv": "P-256",
                      "x": "%s",
                      "y": "%s",
                      "key_ops": ["verify"]
                    }
                  ]
                }
                """.formatted(KEY_ID, x, y));
    }

    private String token(String payloadJson, KeyPair keyPair, String kid) throws Exception {
        String header = base64Url("{\"alg\":\"ES256\",\"typ\":\"JWT\",\"kid\":\"" + kid + "\"}");
        String payload = base64Url(payloadJson);
        String unsignedToken = header + "." + payload;
        return unsignedToken + "." + sign(unsignedToken, keyPair);
    }

    private String sign(String value, KeyPair keyPair) throws Exception {
        Signature signer = Signature.getInstance("SHA256withECDSA");
        signer.initSign(keyPair.getPrivate());
        signer.update(value.getBytes(StandardCharsets.UTF_8));
        return base64Url(toRawSignature(signer.sign()));
    }

    private byte[] toRawSignature(byte[] derSignature) {
        int offset = 2;
        if (derSignature[offset] != 0x02) {
            throw new IllegalArgumentException("Invalid DER signature.");
        }
        int rLength = derSignature[offset + 1];
        byte[] r = Arrays.copyOfRange(derSignature, offset + 2, offset + 2 + rLength);
        offset = offset + 2 + rLength;
        if (derSignature[offset] != 0x02) {
            throw new IllegalArgumentException("Invalid DER signature.");
        }
        int sLength = derSignature[offset + 1];
        byte[] s = Arrays.copyOfRange(derSignature, offset + 2, offset + 2 + sLength);
        byte[] raw = new byte[64];
        System.arraycopy(unsignedFixed(new BigInteger(1, r), 32), 0, raw, 0, 32);
        System.arraycopy(unsignedFixed(new BigInteger(1, s), 32), 0, raw, 32, 32);
        return raw;
    }

    private byte[] unsignedFixed(BigInteger value, int size) {
        byte[] bytes = value.toByteArray();
        if (bytes.length == size) {
            return bytes;
        }
        if (bytes.length == size + 1 && bytes[0] == 0) {
            return Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        byte[] fixed = new byte[size];
        if (bytes.length > size) {
            System.arraycopy(bytes, bytes.length - size, fixed, 0, size);
        } else {
            System.arraycopy(bytes, 0, fixed, size - bytes.length, bytes.length);
        }
        return fixed;
    }

    private String base64Url(String value) {
        return base64Url(value.getBytes(StandardCharsets.UTF_8));
    }

    private String base64Url(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    @Test
    void unknownKeysCannotTriggerRepeatedRemoteRequests() throws Exception {
        KeyPair keyPair = keyPair();
        TestSupabaseAuthService service = new TestSupabaseAuthService(jwks(keyPair));
        ReflectionTestUtils.setField(service, "supabaseJwksUri", JWKS_URI);
        ReflectionTestUtils.setField(service, "supabaseJwtIssuer", ISSUER);
        String payload = "{\"sub\":\"user\",\"iss\":\"" + ISSUER + "\",\"exp\":" + Instant.now().plusSeconds(60).getEpochSecond() + "}";
        service.authenticate(requestWithBearer(token(payload, keyPair, KEY_ID)));
        for (int index = 0; index < 10; index++) {
            String unknown = token(payload, keyPair, "unknown-" + index);
            assertThatThrownBy(() -> service.authenticate(requestWithBearer(unknown))).isInstanceOf(AuthRequiredException.class);
        }
        assertThat(service.fetchCount).isEqualTo(1);
        service.authenticate(requestWithBearer(token(payload, keyPair, KEY_ID)));
        assertThat(service.fetchCount).isEqualTo(1);
    }

    private static class TestSupabaseAuthService extends SupabaseAuthService {
        private final JsonNode jwks;
        private int fetchCount;

        private TestSupabaseAuthService(JsonNode jwks) {
            this.jwks = jwks;
        }

        @Override
        protected JsonNode fetchJwks() {
            fetchCount++;
            return jwks;
        }
    }
}
