package com.teample.service;

import com.teample.dto.subscription.CheckoutResponse;
import com.teample.dto.subscription.SubscriptionResponse;
import com.teample.entity.PaymentRecord;
import com.teample.entity.PlanType;
import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;
import com.teample.repository.PaymentRecordRepository;
import com.teample.repository.SubscriptionRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.service.payment.TossPaymentsClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private final AuthenticatedUser user = new AuthenticatedUser("11111111-2222-3333-4444-555555555555", "박규남", "g@example.com");

    @Test
    void checkoutExposesClientKeyAndDeterministicCustomerKey() {
        Fixture f = new Fixture(true);

        CheckoutResponse checkout = f.service.checkout(user);

        assertThat(checkout.clientKey()).isEqualTo("test_gck_x");
        assertThat(checkout.customerKey()).isEqualTo("tpl-11111111-2222-3333-4444-555555555555");
        assertThat(checkout.amount()).isEqualTo(4900);
        assertThat(checkout.testMode()).isTrue();
        assertThat(SubscriptionService.customerKeyFor("a b/c")).isEqualTo("tpl-abc");
    }

    @Test
    void checkoutFailsWhenPaymentsNotConfigured() {
        Fixture f = new Fixture(false);

        assertThatThrownBy(() -> f.service.checkout(user))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("503");
    }

    @Test
    void confirmIssuesBillingKeyChargesFirstMonthAndGrantsPremium() throws Exception {
        Fixture f = new Fixture(true);
        when(f.subscriptions.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId())).thenReturn(Optional.empty());
        when(f.toss.issueBillingKey("auth-1", "tpl-11111111-2222-3333-4444-555555555555"))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234-****"));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenReturn(new TossPaymentsClient.Payment("pk-1", "sub-1", "DONE", 4900, LocalDateTime.now()));

        SubscriptionResponse response = f.service.confirm(user, "auth-1", "tpl-11111111-2222-3333-4444-555555555555");

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.cardCompany()).isEqualTo("신한");
        assertThat(response.currentPeriodEnd()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(response.nextBillingAt()).isEqualTo(response.currentPeriodEnd());

        ArgumentCaptor<PaymentRecord> record = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(f.payments).save(record.capture());
        assertThat(record.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_DONE);
        assertThat(record.getValue().getPaymentKey()).isEqualTo("pk-1");
        verify(f.plans).grant(eq(user.authUserId()), eq(PlanType.PREMIUM), any(LocalDateTime.class), anyString(), eq("subscription"));
    }

    @Test
    void confirmRejectsForeignCustomerKey() {
        Fixture f = new Fixture(true);

        assertThatThrownBy(() -> f.service.confirm(user, "auth-1", "tpl-someone-else"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void failedFirstChargeReturns402AndDoesNotGrant() throws Exception {
        Fixture f = new Fixture(true);
        when(f.subscriptions.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId())).thenReturn(Optional.empty());
        when(f.toss.issueBillingKey(anyString(), anyString()))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234"));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenThrow(new TossPaymentsClient.TossPaymentsException("REJECT_CARD", "한도 초과"));

        assertThatThrownBy(() -> f.service.confirm(user, "auth-1", "tpl-11111111-2222-3333-4444-555555555555"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("402")
                .hasMessageContaining("한도 초과");
        verify(f.plans, never()).grant(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void cancelKeepsPremiumUntilPeriodEndAndStopsBilling() {
        Fixture f = new Fixture(true);
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        Subscription subscription = active(end);
        when(f.subscriptions.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId())).thenReturn(Optional.of(subscription));

        SubscriptionResponse response = f.service.cancel(user);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getNextBillingAt()).isNull();
        verify(f.plans).grant(eq(user.authUserId()), eq(PlanType.PREMIUM), eq(end), anyString(), eq("subscription"));
    }

    @Test
    void renewalExtendsPeriodOnSuccessAndMarksPastDueAfterThreeFailures() throws Exception {
        Fixture f = new Fixture(true);
        Subscription ok = active(LocalDateTime.now().minusHours(1));
        ok.setNextBillingAt(ok.getCurrentPeriodEnd());
        Subscription failing = active(LocalDateTime.now().minusHours(1));
        failing.setId("s-fail");
        failing.setBillingKey("bk-fail");
        failing.setNextBillingAt(failing.getCurrentPeriodEnd());
        failing.setFailedAttempts(2);
        when(f.subscriptions.findByStatusAndNextBillingAtLessThanEqual(eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(ok, failing));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenReturn(new TossPaymentsClient.Payment("pk-2", "o", "DONE", 4900, LocalDateTime.now()));
        when(f.toss.chargeBilling(eq("bk-fail"), any()))
                .thenThrow(new TossPaymentsClient.TossPaymentsException("INVALID_CARD", "카드 오류"));

        int renewed = f.service.renewDueSubscriptions();

        assertThat(renewed).isEqualTo(1);
        assertThat(ok.getCurrentPeriodEnd()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(ok.getFailedAttempts()).isZero();
        assertThat(failing.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(failing.getFailedAttempts()).isEqualTo(3);
        assertThat(failing.getNextBillingAt()).isNull();
        assertThat(failing.getLastError()).contains("카드 오류");
    }

    private Subscription active(LocalDateTime periodEnd) {
        return Subscription.builder()
                .id("s-1").userId(user.authUserId()).customerKey("tpl-x").billingKey("bk-1")
                .status(SubscriptionStatus.ACTIVE).plan(PlanType.PREMIUM).amount(4900)
                .cardCompany("신한").currentPeriodStart(periodEnd.minusDays(30)).currentPeriodEnd(periodEnd)
                .failedAttempts(0).build();
    }

    private static final class Fixture {
        final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        final PaymentRecordRepository payments = mock(PaymentRecordRepository.class);
        final PlanService plans = mock(PlanService.class);
        final TossPaymentsClient toss = mock(TossPaymentsClient.class);
        final SubscriptionService service;

        Fixture(boolean configured) {
            when(toss.isConfigured()).thenReturn(configured);
            when(toss.clientKey()).thenReturn("test_gck_x");
            when(toss.isTestMode()).thenReturn(true);
            when(subscriptions.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                if (s.getId() == null) s.setId("s-new");
                return s;
            });
            when(payments.save(any(PaymentRecord.class))).thenAnswer(inv -> inv.getArgument(0));
            when(plans.resolve(any(), eq(false))).thenReturn(new PlanService.PlanSnapshot(
                    PlanType.PREMIUM, null, true, new PlanService.PlanUsage(0, 600)));
            service = new SubscriptionService(subscriptions, payments, plans, toss, 4900);
        }
    }
}
