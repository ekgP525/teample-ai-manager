package com.teample.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
public class SupabaseAuthService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${supabase.jwt-secret:${SUPABASE_JWT_SECRET:}}")
    private String supabaseJwtSecret;

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
        if (supabaseJwtSecret == null || supabaseJwtSecret.isBlank()) {
            throw new AuthRequiredException("Supabase backend auth settings are missing.");
        }

        String[] parts = token.split("\\.", -1);
        if (parts.length != 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            throw new AuthRequiredException("Invalid Supabase access token.");
        }

        try {
            JsonNode header = objectMapper.readTree(base64UrlDecode(parts[0]));
            if (!"HS256".equals(optionalText(header, "alg"))) {
                throw new AuthRequiredException("Invalid Supabase access token.");
            }

            byte[] expectedSignature = sign(parts[0] + "." + parts[1]);
            byte[] actualSignature = base64UrlDecode(parts[2]);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                throw new AuthRequiredException("Invalid Supabase access token.");
            }

            JsonNode claims = objectMapper.readTree(base64UrlDecode(parts[1]));
            validateExpiration(claims);
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

    private void validateExpiration(JsonNode claims) {
        JsonNode exp = claims.path("exp");
        if (!exp.canConvertToLong()) {
            throw new AuthRequiredException("Supabase access token is missing expiration.");
        }
        if (Instant.now().getEpochSecond() >= exp.asLong()) {
            throw new AuthRequiredException("Supabase access token is expired.");
        }
    }

    private byte[] sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(supabaseJwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new AuthRequiredException("Supabase JWT verification failed.");
        }
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
}
