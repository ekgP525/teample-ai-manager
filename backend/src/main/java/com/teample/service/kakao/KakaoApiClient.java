package com.teample.service.kakao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 카카오 로그인(REST) + 카카오톡 메시지 "나에게 보내기" API.
 * 사업자 등록 없이 카카오 디벨로퍼스 앱만 있으면 쓸 수 있다.
 * - 인가: GET https://kauth.kakao.com/oauth/authorize?client_id&redirect_uri&response_type=code&scope=talk_message
 * - 토큰: POST https://kauth.kakao.com/oauth/token (authorization_code / refresh_token)
 * - 발송: POST https://kapi.kakao.com/v2/api/talk/memo/default/send (template_object)
 */
@Component
public class KakaoApiClient {

    public static final String SCOPE_TALK_MESSAGE = "talk_message";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String authBaseUrl;
    private final String apiBaseUrl;
    private final String restApiKey;
    private final String clientSecret;

    public KakaoApiClient(
            @Value("${kakao.auth-base-url:https://kauth.kakao.com}") String authBaseUrl,
            @Value("${kakao.api-base-url:https://kapi.kakao.com}") String apiBaseUrl,
            @Value("${kakao.rest-api-key:}") String restApiKey,
            @Value("${kakao.client-secret:}") String clientSecret
    ) {
        this.authBaseUrl = authBaseUrl.replaceAll("/+$", "");
        this.apiBaseUrl = apiBaseUrl.replaceAll("/+$", "");
        this.restApiKey = restApiKey == null ? "" : restApiKey.trim();
        this.clientSecret = clientSecret == null ? "" : clientSecret.trim();
    }

    public boolean isConfigured() {
        return !restApiKey.isBlank();
    }

    public String buildAuthorizeUrl(String redirectUri, String state) {
        return authBaseUrl + "/oauth/authorize"
                + "?client_id=" + encode(restApiKey)
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(SCOPE_TALK_MESSAGE)
                + "&state=" + encode(state);
    }

    public TokenResponse exchangeCode(String code, String redirectUri) throws KakaoApiException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("client_id", restApiKey);
        form.put("redirect_uri", redirectUri);
        form.put("code", code);
        if (!clientSecret.isBlank()) {
            form.put("client_secret", clientSecret);
        }
        return parseToken(postForm(authBaseUrl + "/oauth/token", form, null, "카카오 토큰 발급"));
    }

    public TokenResponse refresh(String refreshToken) throws KakaoApiException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "refresh_token");
        form.put("client_id", restApiKey);
        form.put("refresh_token", refreshToken);
        if (!clientSecret.isBlank()) {
            form.put("client_secret", clientSecret);
        }
        return parseToken(postForm(authBaseUrl + "/oauth/token", form, null, "카카오 토큰 갱신"));
    }

    public String fetchKakaoUserId(String accessToken) throws KakaoApiException {
        JsonNode root = postForm(apiBaseUrl + "/v2/user/me", Map.of(), accessToken, "카카오 사용자 조회");
        return root.path("id").asText("");
    }

    /** 로그인한 사용자 본인의 카카오톡 "나와의 채팅"으로 텍스트 메시지를 보낸다. */
    public void sendMemo(String accessToken, String text, String webUrl, String buttonTitle) throws KakaoApiException {
        ObjectNode template = objectMapper.createObjectNode();
        template.put("object_type", "text");
        template.put("text", text.length() > 200 ? text.substring(0, 197) + "..." : text);
        ObjectNode link = template.putObject("link");
        link.put("web_url", webUrl);
        link.put("mobile_web_url", webUrl);
        if (buttonTitle != null && !buttonTitle.isBlank()) {
            template.put("button_title", buttonTitle);
        }
        String templateJson;
        try {
            templateJson = objectMapper.writeValueAsString(template);
        } catch (IOException e) {
            throw new KakaoApiException("메시지 본문을 만들지 못했습니다.", e);
        }
        JsonNode root = postForm(apiBaseUrl + "/v2/api/talk/memo/default/send",
                Map.of("template_object", templateJson), accessToken, "카카오톡 메시지 발송");
        if (root.path("result_code").asInt(-1) != 0) {
            throw new KakaoApiException("카카오톡 메시지 발송 결과가 실패입니다: " + root);
        }
    }

    public void unlink(String accessToken) throws KakaoApiException {
        postForm(apiBaseUrl + "/v1/user/unlink", Map.of(), accessToken, "카카오 연결 끊기");
    }

    public TokenResponse parseToken(JsonNode root) throws KakaoApiException {
        String accessToken = root.path("access_token").asText("");
        if (accessToken.isBlank()) {
            throw new KakaoApiException("카카오 응답에 access_token이 없습니다.");
        }
        return new TokenResponse(
                accessToken,
                root.path("expires_in").asLong(0),
                root.hasNonNull("refresh_token") ? root.path("refresh_token").asText() : null,
                root.hasNonNull("refresh_token_expires_in") ? root.path("refresh_token_expires_in").asLong(0) : null,
                root.path("scope").asText("")
        );
    }

    private JsonNode postForm(String url, Map<String, String> form, String bearer, String action) throws KakaoApiException {
        StringBuilder body = new StringBuilder();
        form.forEach((key, value) -> {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(encode(key)).append('=').append(encode(value));
        });
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/x-www-form-urlencoded;charset=utf-8")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()));
        if (bearer != null) {
            builder.header("Authorization", "Bearer " + bearer);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new KakaoApiException(action + " 중 네트워크 오류: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KakaoApiException(action + "이(가) 중단되었습니다.", e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        } catch (IOException e) {
            throw new KakaoApiException(action + " 응답을 해석하지 못했습니다.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = root.path("code").isMissingNode()
                    ? root.path("error").asText("HTTP " + response.statusCode())
                    : root.path("code").asText();
            String message = root.path("msg").asText(root.path("error_description").asText(""));
            throw new KakaoApiException(action + " 실패 (" + code + ") " + message, response.statusCode(), code);
        }
        return root;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    public record TokenResponse(
            String accessToken, long expiresInSeconds, String refreshToken, Long refreshExpiresInSeconds, String scope
    ) {
    }

    public static class KakaoApiException extends Exception {
        private final int httpStatus;
        private final String code;

        public KakaoApiException(String message) {
            this(message, 0, null);
        }

        public KakaoApiException(String message, Throwable cause) {
            super(message, cause);
            this.httpStatus = 0;
            this.code = null;
        }

        public KakaoApiException(String message, int httpStatus, String code) {
            super(message);
            this.httpStatus = httpStatus;
            this.code = code;
        }

        public int httpStatus() {
            return httpStatus;
        }

        public String code() {
            return code;
        }

        /** 액세스 토큰 만료(-401) 여부 */
        public boolean isTokenInvalid() {
            return httpStatus == 401 || "-401".equals(code);
        }

        /** talk_message 동의가 없을 때(-402) */
        public boolean isScopeMissing() {
            return "-402".equals(code);
        }

        /** 리프레시 토큰이 무효(invalid_grant, KOE319)라 다시 연결해야 할 때 */
        public boolean isInvalidGrant() {
            if ("invalid_grant".equals(code) || "KOE319".equals(code)) {
                return true;
            }
            String message = getMessage();
            return message != null && (message.contains("invalid_grant") || message.contains("KOE319"));
        }
    }
}
