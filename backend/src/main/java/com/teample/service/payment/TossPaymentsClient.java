package com.teample.service.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

/**
 * 토스페이먼츠 자동결제(빌링) API.
 * - 빌링키 발급: POST /v1/billing/authorizations/issue { authKey, customerKey }
 * - 자동결제 승인: POST /v1/billing/{billingKey} { customerKey, amount, orderId, orderName, ... }
 * 인증은 "시크릿키:"를 base64로 감싼 Basic 헤더다. test_ 키를 쓰면 실제 결제가 일어나지 않는다.
 */
@Component
public class TossPaymentsClient {

    private static final Duration BILLING_TIMEOUT = Duration.ofSeconds(65);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String baseUrl;
    private final String clientKey;
    private final String secretKey;

    public TossPaymentsClient(
            @Value("${payments.toss.base-url:https://api.tosspayments.com}") String baseUrl,
            @Value("${payments.toss.client-key:}") String clientKey,
            @Value("${payments.toss.secret-key:}") String secretKey
    ) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.clientKey = clientKey == null ? "" : clientKey.trim();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
    }

    public boolean isConfigured() {
        return !clientKey.isBlank() && !secretKey.isBlank();
    }

    /** 프론트 SDK 초기화에 쓰는 공개 키. 시크릿 키는 절대 내보내지 않는다. */
    public String clientKey() {
        return clientKey;
    }

    public boolean isTestMode() {
        return secretKey.startsWith("test_");
    }

    public BillingKey issueBillingKey(String authKey, String customerKey) throws TossPaymentsException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("authKey", authKey);
        body.put("customerKey", customerKey);
        JsonNode root = post("/v1/billing/authorizations/issue", body, Duration.ofSeconds(30));
        return parseBillingKey(root);
    }

    public Payment chargeBilling(String billingKey, BillingCharge charge) throws TossPaymentsException {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("customerKey", charge.customerKey());
        body.put("amount", charge.amount());
        body.put("orderId", charge.orderId());
        body.put("orderName", charge.orderName());
        if (charge.customerEmail() != null && !charge.customerEmail().isBlank()) {
            body.put("customerEmail", charge.customerEmail());
        }
        if (charge.customerName() != null && !charge.customerName().isBlank()) {
            body.put("customerName", charge.customerName());
        }
        JsonNode root = post("/v1/billing/" + billingKey, body, BILLING_TIMEOUT);
        return parsePayment(root);
    }

    BillingKey parseBillingKey(JsonNode root) throws TossPaymentsException {
        String billingKey = root.path("billingKey").asText("");
        if (billingKey.isBlank()) {
            throw new TossPaymentsException("BILLING_KEY_MISSING", "빌링키 발급 응답에 billingKey가 없습니다.");
        }
        JsonNode card = root.path("card");
        String company = firstText(card.path("company"), root.path("cardCompany"));
        String number = firstText(card.path("number"), root.path("cardNumber"));
        return new BillingKey(billingKey, root.path("customerKey").asText(""), company, number);
    }

    Payment parsePayment(JsonNode root) {
        return new Payment(
                root.path("paymentKey").asText(""),
                root.path("orderId").asText(""),
                root.path("status").asText(""),
                root.path("totalAmount").asInt(0),
                parseDateTime(root.path("approvedAt").asText(null))
        );
    }

    private JsonNode post(String path, JsonNode body, Duration timeout) throws TossPaymentsException {
        if (!isConfigured()) {
            throw new TossPaymentsException("NOT_CONFIGURED", "결제 키가 설정되지 않았습니다.");
        }
        String basic = Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                    .header("Authorization", "Basic " + basic)
                    .header("Content-Type", "application/json")
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
        } catch (IOException e) {
            throw new TossPaymentsException("REQUEST_BUILD_FAILED", "결제 요청을 만들지 못했습니다.", e);
        }

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new TossPaymentsException("NETWORK_ERROR", "결제 서버와 통신하지 못했습니다: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TossPaymentsException("INTERRUPTED", "결제 요청이 중단되었습니다.", e);
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
        } catch (IOException e) {
            throw new TossPaymentsException("INVALID_RESPONSE", "결제 서버 응답을 해석하지 못했습니다.", e);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String code = root.path("code").asText("HTTP_" + response.statusCode());
            String message = root.path("message").asText("결제 요청이 거절되었습니다.");
            throw new TossPaymentsException(code, message);
        }
        return root;
    }

    private static String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }

    public record BillingKey(String billingKey, String customerKey, String cardCompany, String cardNumber) {
    }

    public record BillingCharge(
            String customerKey, int amount, String orderId, String orderName, String customerEmail, String customerName
    ) {
    }

    public record Payment(String paymentKey, String orderId, String status, int totalAmount, LocalDateTime approvedAt) {
        public boolean isDone() {
            return "DONE".equalsIgnoreCase(status);
        }
    }

    public static class TossPaymentsException extends Exception {
        private final String code;

        public TossPaymentsException(String code, String message) {
            super(message);
            this.code = code;
        }

        public TossPaymentsException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }
}
