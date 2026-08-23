package com.teample.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;

@Service
public class SupabaseAuthService {

    private static final String SUPABASE_ES256_ALG = "ES256";
    private static final String SUPABASE_EC_KEY_TYPE = "EC";
    private static final String SUPABASE_P256_CURVE = "P-256";
    private static final String JAVA_P256_CURVE = "secp256r1";
    private static final Duration JWKS_CACHE_TTL = Duration.ofMinutes(10);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Clock clock = Clock.systemUTC();

    @Value("${supabase.jwks-uri:${SUPABASE_JWKS_URI:}}")
    private String supabaseJwksUri;

    @Value("${supabase.jwt-issuer:${SUPABASE_JWT_ISSUER:}}")
    private String supabaseJwtIssuer;

    private volatile CachedJwks cachedJwks;

    public AuthenticatedUser authenticate(HttpServletRequest request) {
        String token = resolveBearerToken(request);
        JsonNode claims = verifyAndReadClaims(token);
        String authUserId = requiredText(claims, "sub");
        String email = optionalText(claims, "email");
        String memberKey = resolveMemberKey(claims, email, authUserId);
        return new AuthenticatedUser(authUserId, memberKey, email);
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AuthRequiredException("Authorization bearer token is required.");
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (token.isBlank()) {
            throw new AuthRequiredException("Authorization bearer token is empty.");
        }
        return token;
    }

    private JsonNode verifyAndReadClaims(String token) {
        if (supabaseJwksUri == null || supabaseJwksUri.isBlank()) {
            throw new AuthRequiredException("Supabase JWKS settings are missing.");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new AuthRequiredException("Invalid Supabase access token.");
        }

        try {
            JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
            String alg = optionalText(header, "alg");
            if (!SUPABASE_ES256_ALG.equals(alg)) {
                throw new AuthRequiredException("Unsupported Supabase access token algorithm.");
            }
            String kid = requiredText(header, "kid");

            JsonNode jwk = findJwk(kid);
            ECPublicKey publicKey = toEcPublicKey(jwk);
            String unsignedToken = parts[0] + "." + parts[1];
            if (!verifyEs256(unsignedToken, parts[2], publicKey)) {
                throw new AuthRequiredException("Invalid Supabase access token.");
            }

            JsonNode claims = objectMapper.readTree(base64UrlDecode(parts[1]));
            validateExpiration(claims);
            validateIssuer(claims);
            return claims;
        } catch (IOException e) {
            throw new AuthRequiredException("Invalid Supabase access token.");
        } catch (RuntimeException e) {
            if (e instanceof AuthRequiredException authRequiredException) {
                throw authRequiredException;
            }
            throw new AuthRequiredException("Invalid Supabase access token.");
        }
    }

    private JsonNode findJwk(String kid) {
        JsonNode jwks = trustedJwks(kid);
        for (JsonNode key : jwks.path("keys")) {
            if (kid.equals(optionalText(key, "kid"))
                    && SUPABASE_ES256_ALG.equals(optionalText(key, "alg"))
                    && SUPABASE_EC_KEY_TYPE.equals(optionalText(key, "kty"))
                    && SUPABASE_P256_CURVE.equals(optionalText(key, "crv"))) {
                return key;
            }
        }
        throw new AuthRequiredException("Supabase signing key was not found.");
    }

    private JsonNode trustedJwks(String kid) {
        Instant now = Instant.now(clock);
        CachedJwks current = cachedJwks;
        if (current != null && now.isBefore(current.expiresAt()) && containsKid(current.jwks(), kid)) {
            return current.jwks();
        }

        JsonNode fresh = fetchJwks();
        cachedJwks = new CachedJwks(fresh, now.plus(JWKS_CACHE_TTL));
        return fresh;
    }

    protected JsonNode fetchJwks() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(supabaseJwksUri.trim()))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new AuthRequiredException("Supabase JWKS endpoint is unavailable.");
            }
            return objectMapper.readTree(response.body());
        } catch (IllegalArgumentException | IOException e) {
            throw new AuthRequiredException("Supabase JWKS endpoint is unavailable.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AuthRequiredException("Supabase JWKS endpoint is unavailable.");
        }
    }

    private boolean containsKid(JsonNode jwks, String kid) {
        for (JsonNode key : jwks.path("keys")) {
            if (kid.equals(optionalText(key, "kid"))) {
                return true;
            }
        }
        return false;
    }

    private ECPublicKey toEcPublicKey(JsonNode jwk) {
        try {
            BigInteger x = new BigInteger(1, base64UrlDecode(requiredText(jwk, "x")));
            BigInteger y = new BigInteger(1, base64UrlDecode(requiredText(jwk, "y")));
            AlgorithmParameters parameters = AlgorithmParameters.getInstance(SUPABASE_EC_KEY_TYPE);
            parameters.init(new ECGenParameterSpec(JAVA_P256_CURVE));
            ECParameterSpec ecParameterSpec = parameters.getParameterSpec(ECParameterSpec.class);
            ECPublicKeySpec publicKeySpec = new ECPublicKeySpec(new ECPoint(x, y), ecParameterSpec);
            return (ECPublicKey) KeyFactory.getInstance(SUPABASE_EC_KEY_TYPE).generatePublic(publicKeySpec);
        } catch (Exception e) {
            throw new AuthRequiredException("Invalid Supabase signing key.");
        }
    }

    private boolean verifyEs256(String unsignedToken, String encodedSignature, ECPublicKey publicKey) {
        try {
            byte[] rawSignature = base64UrlDecode(encodedSignature);
            if (rawSignature.length != 64) {
                return false;
            }
            Signature verifier = Signature.getInstance("SHA256withECDSA");
            verifier.initVerify(publicKey);
            verifier.update(unsignedToken.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(toDerSignature(rawSignature));
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] toDerSignature(byte[] rawSignature) {
        byte[] r = toUnsignedInteger(Arrays.copyOfRange(rawSignature, 0, 32));
        byte[] s = toUnsignedInteger(Arrays.copyOfRange(rawSignature, 32, 64));
        int length = 2 + r.length + 2 + s.length;
        return concat(
                new byte[]{0x30, (byte) length, 0x02, (byte) r.length},
                r,
                new byte[]{0x02, (byte) s.length},
                s
        );
    }

    private byte[] toUnsignedInteger(byte[] value) {
        return new BigInteger(1, value).toByteArray();
    }

    private byte[] concat(byte[]... values) {
        int length = Arrays.stream(values).mapToInt(value -> value.length).sum();
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }

    private void validateExpiration(JsonNode claims) {
        JsonNode exp = claims.path("exp");
        if (!exp.canConvertToLong()) {
            throw new AuthRequiredException("Supabase access token is missing expiration.");
        }
        if (Instant.now(clock).getEpochSecond() >= exp.asLong()) {
            throw new AuthRequiredException("Supabase access token is expired.");
        }
    }

    private void validateIssuer(JsonNode claims) {
        if (supabaseJwtIssuer == null || supabaseJwtIssuer.isBlank()) {
            return;
        }
        String issuer = optionalText(claims, "iss");
        if (!normalizeIssuer(supabaseJwtIssuer).equals(normalizeIssuer(issuer))) {
            throw new AuthRequiredException("Invalid Supabase access token issuer.");
        }
    }

    private String normalizeIssuer(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private byte[] base64UrlDecode(String value) {
        return Base64.getUrlDecoder().decode(value);
    }

    private String resolveMemberKey(JsonNode claims, String email, String authUserId) {
        JsonNode metadata = claims.path("user_metadata");
        String metadataName = firstText(metadata, "name", "full_name", "display_name");
        if (metadataName != null) {
            return metadataName;
        }
        if (email != null && email.contains("@")) {
            return email.substring(0, email.indexOf('@'));
        }
        return authUserId;
    }

    private String requiredText(JsonNode node, String field) {
        String value = optionalText(node, field);
        if (value == null) {
            throw new AuthRequiredException("Supabase access token is missing " + field + ".");
        }
        return value;
    }

    private String optionalText(JsonNode node, String field) {
        if (node == null || node.path(field).isMissingNode() || node.path(field).isNull()) {
            return null;
        }
        String value = node.path(field).asText();
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = optionalText(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private record CachedJwks(JsonNode jwks, Instant expiresAt) {
    }
}
