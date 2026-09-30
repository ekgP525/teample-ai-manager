package com.teample.service.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TossPaymentsClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void configurationAndTestModeDetection() {
        assertThat(new TossPaymentsClient("https://api.tosspayments.com", "", "").isConfigured()).isFalse();
        TossPaymentsClient test = new TossPaymentsClient("https://api.tosspayments.com/", "test_gck_a", "test_gsk_b");
        assertThat(test.isConfigured()).isTrue();
        assertThat(test.isTestMode()).isTrue();
        assertThat(test.clientKey()).isEqualTo("test_gck_a");
        assertThat(new TossPaymentsClient("x", "live_ck", "live_sk").isTestMode()).isFalse();
    }

    @Test
    void parsesBillingKeyResponseWithNestedCard() throws Exception {
        TossPaymentsClient client = new TossPaymentsClient("x", "c", "s");
        String json = """
                {"billingKey":"bk_1","customerKey":"tpl-u","method":"카드","mId":"tosspayments",
                 "card":{"company":"신한","number":"1234****","cardType":"신용"},"authenticatedAt":"2026-10-01T09:00:00+09:00"}
                """;

        TossPaymentsClient.BillingKey key = client.parseBillingKey(objectMapper.readTree(json));

        assertThat(key.billingKey()).isEqualTo("bk_1");
        assertThat(key.cardCompany()).isEqualTo("신한");
        assertThat(key.cardNumber()).isEqualTo("1234****");
    }

    @Test
    void parsesLegacyFlatCardFieldsAndRejectsMissingKey() throws Exception {
        TossPaymentsClient client = new TossPaymentsClient("x", "c", "s");

        TossPaymentsClient.BillingKey key = client.parseBillingKey(objectMapper.readTree(
                "{\"billingKey\":\"bk_2\",\"cardCompany\":\"국민\",\"cardNumber\":\"5555****\"}"));
        assertThat(key.cardCompany()).isEqualTo("국민");

        assertThatThrownBy(() -> client.parseBillingKey(objectMapper.readTree("{}")))
                .isInstanceOf(TossPaymentsClient.TossPaymentsException.class);
    }

    @Test
    void parsesPaymentApprovalTimestamps() throws Exception {
        TossPaymentsClient client = new TossPaymentsClient("x", "c", "s");

        TossPaymentsClient.Payment payment = client.parsePayment(objectMapper.readTree(
                "{\"paymentKey\":\"pk\",\"orderId\":\"o\",\"status\":\"DONE\",\"totalAmount\":4900,\"approvedAt\":\"2026-10-01T09:10:00+09:00\"}"));

        assertThat(payment.isDone()).isTrue();
        assertThat(payment.totalAmount()).isEqualTo(4900);
        assertThat(payment.approvedAt()).isNotNull();
        assertThat(payment.approvedAt().getHour()).isEqualTo(9);
    }
}
