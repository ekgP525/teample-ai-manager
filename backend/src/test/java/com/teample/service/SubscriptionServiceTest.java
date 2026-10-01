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
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SubscriptionServiceTest {

    private static final String CUSTOMER_KEY = "tpl-11111111-2222-3333-4444-555555555555";

    private final AuthenticatedUser user = new AuthenticatedUser("11111111-2222-3333-4444-555555555555", "박규남", "g@example.com");

    @Test
    void checkoutExposesClientKeyAndDeterministicCustomerKey() {
        Fixture f = new Fixture(true);

        CheckoutResponse checkout = f.service.checkout(user);

        assertThat(checkout.clientKey()).isEqualTo("test_gck_x");
        assertThat(checkout.customerKey()).isEqualTo(CUSTOMER_KEY);
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
        when(f.toss.issueBillingKey("auth-1", CUSTOMER_KEY))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234-****"));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenReturn(new TossPaymentsClient.Payment("pk-1", "sub-1", "DONE", 4900, LocalDateTime.now()));

        SubscriptionResponse response = f.service.confirm(user, "auth-1", CUSTOMER_KEY);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(response.cardCompany()).isEqualTo("신한");
        assertThat(response.currentPeriodEnd()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(response.nextBillingAt()).isEqualTo(response.currentPeriodEnd());

        ArgumentCaptor<PaymentRecord> record = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(f.payments, times(2)).save(record.capture()); // PENDING 생성 → DONE 갱신
        assertThat(record.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_DONE);
        assertThat(record.getValue().getPaymentKey()).isEqualTo("pk-1");
        assertThat(record.getValue().getPeriodStart()).isNotNull();
        verify(f.plans).grantAtLeast(eq(user.authUserId()), eq(PlanType.PREMIUM), any(LocalDateTime.class), anyString(), eq("subscription"));
    }

    @Test
    void confirmCreatesPendingRecordWithDeterministicOrderIdBeforeCallingToss() throws Exception {
        Fixture f = new Fixture(true);
        when(f.toss.issueBillingKey(anyString(), anyString()))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234"));
        AtomicReference<String> statusWhenCharged = new AtomicReference<>();
        AtomicReference<String> chargedOrderId = new AtomicReference<>();
        when(f.toss.chargeBilling(eq("bk-1"), any())).thenAnswer(inv -> {
            TossPaymentsClient.BillingCharge charge = inv.getArgument(1);
            chargedOrderId.set(charge.orderId());
            statusWhenCharged.set(f.recordByOrderId(charge.orderId()).getStatus());
            return new TossPaymentsClient.Payment("pk-1", charge.orderId(), "DONE", 4900, LocalDateTime.now());
        });

        f.service.confirm(user, "auth-1", CUSTOMER_KEY);

        assertThat(statusWhenCharged.get()).isEqualTo(PaymentRecord.STATUS_PENDING);
        String expectedPrefix = "sub-" + "s-new".replace("-", "") + "-";
        assertThat(chargedOrderId.get()).startsWith(expectedPrefix).hasSizeLessThanOrEqualTo(64);
        String minute = chargedOrderId.get().substring(expectedPrefix.length());
        assertThat(minute).hasSize(12);
        LocalDateTime periodStart = f.recordByOrderId(chargedOrderId.get()).getPeriodStart();
        assertThat(periodStart).isNotNull();
        assertThat(SubscriptionStore.orderIdFor("s-new", periodStart)).isEqualTo(chargedOrderId.get());
        assertThat(minute).isEqualTo(periodStart.format(DateTimeFormatter.ofPattern("yyyyMMddHHmm")));
    }

    @Test
    void orderIdIsDeterministicPerSubscriptionAndPeriod() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 1, 9, 30, 45);
        String uuid = "0f3d8c2a-7b1e-4c55-9a8d-1234567890ab";

        String orderId = SubscriptionStore.orderIdFor(uuid, start);

        assertThat(orderId).isEqualTo("sub-0f3d8c2a7b1e-202610010930");
        assertThat(SubscriptionStore.orderIdFor(uuid, start.withSecond(1))).isEqualTo(orderId);
        assertThat(SubscriptionStore.orderIdFor(uuid, start.plusMinutes(1))).isNotEqualTo(orderId);
        assertThat(orderId.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void confirmRejectsForeignCustomerKey() {
        Fixture f = new Fixture(true);

        assertThatThrownBy(() -> f.service.confirm(user, "auth-1", "tpl-someone-else"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    @Test
    void failedFirstChargeReturns402PersistsPastDueAndDoesNotGrant() throws Exception {
        Fixture f = new Fixture(true);
        when(f.toss.issueBillingKey(anyString(), anyString()))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234"));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenThrow(new TossPaymentsClient.TossPaymentsException("REJECT_CARD", "한도 초과"));

        assertThatThrownBy(() -> f.service.confirm(user, "auth-1", CUSTOMER_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("402")
                .hasMessageContaining("한도 초과");

        verify(f.plans, never()).grantAtLeast(anyString(), any(), any(), anyString(), anyString());
        verify(f.plans, never()).grant(anyString(), any(), any(), anyString(), anyString());
        Subscription persisted = f.subscriptionsById.get("s-new");
        assertThat(persisted.getStatus()).isEqualTo(SubscriptionStatus.PAST_DUE);
        assertThat(persisted.getLastError()).contains("한도 초과");
        assertThat(persisted.getNextBillingAt()).isNull();
        ArgumentCaptor<PaymentRecord> record = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(f.payments, atLeastOnce()).save(record.capture());
        assertThat(record.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_FAILED);
    }

    @Test
    void networkErrorLeavesRecordPendingAndReturns502WithoutSecondCharge() throws Exception {
        Fixture f = new Fixture(true);
        when(f.toss.issueBillingKey(anyString(), anyString()))
                .thenReturn(new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234"));
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenThrow(new TossPaymentsClient.TossPaymentsException("NETWORK_ERROR", "timeout"));

        assertThatThrownBy(() -> f.service.confirm(user, "auth-1", CUSTOMER_KEY))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");

        verify(f.toss, times(1)).chargeBilling(anyString(), any());
        ArgumentCaptor<PaymentRecord> record = ArgumentCaptor.forClass(PaymentRecord.class);
        verify(f.payments, atLeastOnce()).save(record.capture());
        assertThat(record.getValue().getStatus()).isEqualTo(PaymentRecord.STATUS_PENDING);
        assertThat(f.subscriptionsById.get("s-new").getStatus()).isEqualTo(SubscriptionStatus.ACTIVE);
        verify(f.plans, never()).grantAtLeast(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void concurrentConfirmForSameUserIsRejectedWith409() throws Exception {
        Fixture f = new Fixture(true);
        CountDownLatch tossEntered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(f.toss.issueBillingKey(anyString(), anyString())).thenAnswer(inv -> {
            tossEntered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return new TossPaymentsClient.BillingKey("bk-1", "tpl-x", "신한", "1234");
        });
        when(f.toss.chargeBilling(eq("bk-1"), any()))
                .thenReturn(new TossPaymentsClient.Payment("pk-1", "o", "DONE", 4900, LocalDateTime.now()));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<SubscriptionResponse> first = executor.submit(() -> f.service.confirm(user, "auth-1", CUSTOMER_KEY));
            assertThat(tossEntered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> f.service.confirm(user, "auth-2", CUSTOMER_KEY))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("409")
                    .hasMessageContaining("처리 중");

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo(SubscriptionStatus.ACTIVE);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
        verify(f.toss, times(1)).issueBillingKey(anyString(), anyString());
    }

    @Test
    void cancelKeepsPremiumUntilPeriodEndAndStopsBilling() {
        Fixture f = new Fixture(true);
        LocalDateTime end = LocalDateTime.now().plusDays(10);
        Subscription subscription = f.register(active(end));

        SubscriptionResponse response = f.service.cancel(user);

        assertThat(response.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.getNextBillingAt()).isNull();
        verify(f.plans).shortenSubscriptionGrant(eq(user.authUserId()), eq(end), anyString());
        verify(f.plans, never()).grant(anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    void renewalExtendsPeriodOnSuccessAndMarksPastDueAfterThreeFailures() throws Exception {
        Fixture f = new Fixture(true);
        Subscription ok = f.register(active(LocalDateTime.now().minusHours(1)));
        ok.setNextBillingAt(ok.getCurrentPeriodEnd());
        Subscription failing = active(LocalDateTime.now().minusHours(1));
        failing.setId("s-fail");
        failing.setBillingKey("bk-fail");
        failing.setNextBillingAt(failing.getCurrentPeriodEnd());
        failing.setFailedAttempts(2);
        f.register(failing);
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
        verify(f.plans).grantAtLeast(eq(user.authUserId()), eq(PlanType.PREMIUM), any(LocalDateTime.class), anyString(), eq("subscription"));
    }

    @Test
    void renewalExtendsFromPeriodEndWithinGraceAndFromNowAfterGrace() {
        LocalDateTime now = LocalDateTime.of(2026, 10, 10, 12, 0);
        Subscription withinGrace = active(now.minusDays(2));
        Subscription afterGrace = active(now.minusDays(5));

        assertThat(SubscriptionService.renewalPeriodStart(withinGrace, now)).isEqualTo(now.minusDays(2));
        assertThat(SubscriptionService.renewalPeriodStart(afterGrace, now)).isEqualTo(now);
    }

    @Test
    void renewalSkipsSubscriptionWithRecentPendingRecord() throws Exception {
        Fixture f = new Fixture(true);
        Subscription due = f.register(active(LocalDateTime.now().minusHours(1)));
        due.setNextBillingAt(due.getCurrentPeriodEnd());
        when(f.subscriptions.findByStatusAndNextBillingAtLessThanEqual(eq(SubscriptionStatus.ACTIVE), any()))
                .thenReturn(List.of(due));
        when(f.payments.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc("s-1", PaymentRecord.STATUS_PENDING))
                .thenReturn(Optional.of(PaymentRecord.builder()
                        .id("pr-1").subscriptionId("s-1").userId(user.authUserId()).orderId("sub-x")
                        .amount(4900).status(PaymentRecord.STATUS_PENDING)
                        .createdAt(LocalDateTime.now().minusMinutes(10)).build()));

        assertThat(f.service.renewDueSubscriptions()).isZero();
        verify(f.toss, never()).chargeBilling(anyString(), any());
    }

    private Subscription active(LocalDateTime periodEnd) {
        return Subscription.builder()
                .id("s-1").userId(user.authUserId()).customerKey("tpl-x").billingKey("bk-1")
                .status(SubscriptionStatus.ACTIVE).plan(PlanType.PREMIUM).amount(4900)
                .cardCompany("신한").currentPeriodStart(periodEnd.minusDays(30)).currentPeriodEnd(periodEnd)
                .failedAttempts(0).build();
    }

    /** 실제 SubscriptionStore에 mock 저장소를 물려 트랜잭션 분리 구조를 그대로 검증한다. */
    private static final class Fixture {
        final SubscriptionRepository subscriptions = mock(SubscriptionRepository.class);
        final PaymentRecordRepository payments = mock(PaymentRecordRepository.class);
        final PlanService plans = mock(PlanService.class);
        final TossPaymentsClient toss = mock(TossPaymentsClient.class);
        final Map<String, Subscription> subscriptionsById = new ConcurrentHashMap<>();
        final Map<String, PaymentRecord> recordsByOrderId = new ConcurrentHashMap<>();
        final SubscriptionService service;

        Fixture(boolean configured) {
            when(toss.isConfigured()).thenReturn(configured);
            when(toss.clientKey()).thenReturn("test_gck_x");
            when(toss.isTestMode()).thenReturn(true);
            when(subscriptions.save(any(Subscription.class))).thenAnswer(inv -> {
                Subscription s = inv.getArgument(0);
                if (s.getId() == null) s.setId("s-new");
                subscriptionsById.put(s.getId(), s);
                return s;
            });
            when(subscriptions.findById(anyString()))
                    .thenAnswer(inv -> Optional.ofNullable(subscriptionsById.get(inv.<String>getArgument(0))));
            when(subscriptions.findFirstByUserIdOrderByCreatedAtDesc(anyString())).thenAnswer(inv -> subscriptionsById.values().stream()
                    .filter(s -> s.getUserId().equals(inv.<String>getArgument(0)))
                    .findFirst());
            when(payments.save(any(PaymentRecord.class))).thenAnswer(inv -> {
                PaymentRecord r = inv.getArgument(0);
                if (r.getId() == null) r.setId("pr-" + r.getOrderId());
                recordsByOrderId.put(r.getOrderId(), r);
                return r;
            });
            when(payments.findByOrderId(anyString()))
                    .thenAnswer(inv -> Optional.ofNullable(recordsByOrderId.get(inv.<String>getArgument(0))));
            when(plans.resolve(any(), eq(false))).thenReturn(new PlanService.PlanSnapshot(
                    PlanType.PREMIUM, null, true, new PlanService.PlanUsage(0, 600)));
            service = new SubscriptionService(new SubscriptionStore(subscriptions, payments, plans), plans, toss, 4900);
        }

        Subscription register(Subscription subscription) {
            subscriptionsById.put(subscription.getId(), subscription);
            return subscription;
        }

        PaymentRecord recordByOrderId(String orderId) {
            return recordsByOrderId.get(orderId);
        }
    }
}
