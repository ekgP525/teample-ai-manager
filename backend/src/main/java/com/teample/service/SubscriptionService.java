package com.teample.service;

import com.teample.dto.subscription.CheckoutResponse;
import com.teample.dto.subscription.PaymentResponse;
import com.teample.dto.subscription.SubscriptionResponse;
import com.teample.entity.PaymentRecord;
import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;
import com.teample.security.AuthenticatedUser;
import com.teample.service.payment.TossPaymentsClient;
import com.teample.service.payment.TossPaymentsClient.TossPaymentsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 프리미엄 월 구독. 토스페이먼츠 빌링키로 첫 결제와 매달 자동 결제를 처리하고,
 * 결제가 성공할 때마다 PlanService에 프리미엄 만료일을 갱신한다.
 *
 * 이 클래스의 confirm/renewDueSubscriptions는 일부러 트랜잭션이 없다. 토스 호출은 DB 트랜잭션 밖에서 하고,
 * DB 변경은 SubscriptionStore의 짧은 트랜잭션으로 나눠 실행한다. 사용자별 락으로 같은 사용자의 결제가 겹치지 않게 한다.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    static final Duration PENDING_RETRY_AGE = Duration.ofHours(1);
    private static final Set<String> UNKNOWN_OUTCOME_CODES = Set.of("NETWORK_ERROR", "INTERRUPTED");

    private final SubscriptionStore store;
    private final PlanService planService;
    private final TossPaymentsClient tossPaymentsClient;
    private final int priceKrw;
    private final Map<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public SubscriptionService(
            SubscriptionStore store,
            PlanService planService,
            TossPaymentsClient tossPaymentsClient,
            @Value("${app.plan.premium-price-krw:4900}") int priceKrw
    ) {
        this.store = store;
        this.planService = planService;
        this.tossPaymentsClient = tossPaymentsClient;
        this.priceKrw = priceKrw;
    }

    /** 결제창을 열기 위한 값. 시크릿 키는 절대 포함하지 않는다. */
    public CheckoutResponse checkout(AuthenticatedUser user) {
        requireUser(user);
        ensureConfigured();
        return new CheckoutResponse(
                tossPaymentsClient.clientKey(),
                customerKeyFor(user.authUserId()),
                priceKrw,
                SubscriptionStore.ORDER_NAME,
                user.email(),
                user.memberKey(),
                tossPaymentsClient.isTestMode()
        );
    }

    /**
     * 결제창에서 돌아온 authKey로 빌링키를 발급하고 첫 달을 결제한다.
     * 이미 구독 중이면 카드만 바꾸고 결제하지 않는다. 같은 사용자의 confirm이 겹치면 409.
     */
    public SubscriptionResponse confirm(AuthenticatedUser user, String authKey, String customerKey) {
        requireUser(user);
        ensureConfigured();
        if (authKey == null || authKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authKey가 비어 있습니다.");
        }
        if (!customerKeyFor(user.authUserId()).equals(customerKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerKey가 현재 사용자와 일치하지 않습니다.");
        }

        ReentrantLock lock = lockFor(user.authUserId());
        if (!lock.tryLock()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "구독 결제를 처리 중입니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
            return confirmLocked(user, authKey, customerKey);
        } finally {
            lock.unlock();
        }
    }

    private SubscriptionResponse confirmLocked(AuthenticatedUser user, String authKey, String customerKey) {
        TossPaymentsClient.BillingKey issued;
        try {
            issued = tossPaymentsClient.issueBillingKey(authKey, customerKey);
        } catch (TossPaymentsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카드 등록에 실패했습니다: " + e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now();
        SubscriptionStore.BillingKeyApplied applied = store.applyBillingKey(
                user.authUserId(), customerKey, issued, priceKrw, now);
        Subscription subscription = applied.subscription();
        if (!applied.chargeNeeded()) {
            return toResponse(subscription, user);
        }

        LocalDateTime periodStart = now;
        PaymentRecord record = store.beginPayment(subscription, periodStart);
        if (PaymentRecord.STATUS_DONE.equals(record.getStatus())) {
            return toResponse(store.extendPeriod(subscription.getId(), periodStart), user);
        }

        ChargeOutcome outcome = charge(subscription, record, user.email(), user.memberKey());
        switch (outcome.status()) {
            case DONE -> {
                store.markDone(record, outcome.payment());
                return toResponse(store.extendPeriod(subscription.getId(), periodStart), user);
            }
            case FAILED -> {
                store.markFailed(record, outcome.failureMessage(), outcome.paymentKey());
                store.markPastDue(subscription.getId(), outcome.failureMessage());
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                        "첫 결제에 실패했습니다: " + outcome.failureMessage());
            }
            default -> {
                // 결과를 모른다. 기록은 PENDING으로 두고 자동으로 다시 청구하지 않는다.
                log.warn("첫 결제 결과를 확인하지 못했습니다 (user={}, orderId={}): {}",
                        subscription.getUserId(), record.getOrderId(), outcome.failureMessage());
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                        "결제 서버와 통신하지 못했습니다. 잠시 후 구독 상태를 다시 확인해 주세요.");
            }
        }
    }

    public SubscriptionResponse cancel(AuthenticatedUser user) {
        requireUser(user);
        Subscription subscription = store.findLatest(user.authUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구독 내역이 없습니다."));
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return toResponse(subscription, user);
        }
        return toResponse(store.cancel(subscription.getId(), LocalDateTime.now()), user);
    }

    public Optional<SubscriptionResponse> find(AuthenticatedUser user) {
        requireUser(user);
        return store.findLatest(user.authUserId())
                .map(subscription -> toResponse(subscription, user));
    }

    public List<PaymentResponse> payments(AuthenticatedUser user) {
        requireUser(user);
        return store.recentPayments(user.authUserId()).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /** 스케줄러가 호출. 결제일이 지난 ACTIVE 구독을 하나씩, 각자 트랜잭션으로 갱신한다. */
    public int renewDueSubscriptions() {
        if (!tossPaymentsClient.isConfigured()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        int renewed = 0;
        for (Subscription due : store.findDue(now)) {
            ReentrantLock lock = lockFor(due.getUserId());
            if (!lock.tryLock()) {
                log.info("구독 {}은 다른 결제가 진행 중이라 이번 갱신을 건너뜁니다.", due.getId());
                continue;
            }
            try {
                if (renewOne(due.getId(), now)) {
                    renewed++;
                }
            } catch (RuntimeException e) {
                log.error("구독 {} 갱신 중 오류", due.getId(), e);
            } finally {
                lock.unlock();
            }
        }
        return renewed;
    }

    private boolean renewOne(String subscriptionId, LocalDateTime now) {
        Subscription subscription = store.findById(subscriptionId).orElse(null);
        if (subscription == null || subscription.getStatus() != SubscriptionStatus.ACTIVE
                || subscription.getNextBillingAt() == null || subscription.getNextBillingAt().isAfter(now)) {
            return false;
        }
        Optional<PaymentRecord> pending = store.findLatestPending(subscription.getId());
        if (pending.isPresent() && pending.get().getCreatedAt() != null
                && pending.get().getCreatedAt().isAfter(now.minus(PENDING_RETRY_AGE))) {
            log.info("구독 {}에 결과를 모르는 결제가 있어 1시간 동안 재청구하지 않습니다 (orderId={}).",
                    subscription.getId(), pending.get().getOrderId());
            return false;
        }

        LocalDateTime periodStart = renewalPeriodStart(subscription, now);
        PaymentRecord record = store.beginPayment(subscription, periodStart);
        if (PaymentRecord.STATUS_DONE.equals(record.getStatus())) {
            store.extendPeriod(subscription.getId(), periodStart);
            return true;
        }

        ChargeOutcome outcome = charge(subscription, record, null, null);
        switch (outcome.status()) {
            case DONE -> {
                store.markDone(record, outcome.payment());
                store.extendPeriod(subscription.getId(), periodStart);
                return true;
            }
            case FAILED -> {
                store.markFailed(record, outcome.failureMessage(), outcome.paymentKey());
                Subscription updated = store.recordRenewalFailure(subscription.getId(), outcome.failureMessage(), now);
                if (updated.getStatus() == SubscriptionStatus.PAST_DUE) {
                    log.warn("구독 자동결제 {}회 실패, 결제 중단 (user={})", updated.getFailedAttempts(), updated.getUserId());
                }
                return false;
            }
            default -> {
                log.warn("구독 {} 자동결제 결과를 확인하지 못했습니다 (orderId={}): {}",
                        subscription.getId(), record.getOrderId(), outcome.failureMessage());
                return false;
            }
        }
    }

    /** 기간 종료 후 유예(3일) 안이면 종료 시점부터 이어 붙이고, 그보다 늦었으면 지금부터 새 기간을 연다. */
    static LocalDateTime renewalPeriodStart(Subscription subscription, LocalDateTime now) {
        LocalDateTime periodEnd = subscription.getCurrentPeriodEnd();
        if (periodEnd != null && !now.isAfter(periodEnd.plusDays(SubscriptionStore.GRACE_DAYS))) {
            return periodEnd;
        }
        return now;
    }

    /** 토스 자동결제 승인. DB 트랜잭션 밖에서만 부른다. */
    private ChargeOutcome charge(Subscription subscription, PaymentRecord record, String customerEmail, String customerName) {
        try {
            TossPaymentsClient.Payment payment = tossPaymentsClient.chargeBilling(
                    subscription.getBillingKey(),
                    new TossPaymentsClient.BillingCharge(
                            subscription.getCustomerKey(), record.getAmount(), record.getOrderId(),
                            SubscriptionStore.ORDER_NAME, customerEmail, customerName));
            if (payment.isDone()) {
                return ChargeOutcome.done(payment);
            }
            return ChargeOutcome.failed("결제 상태가 완료가 아닙니다: " + payment.status(), payment.paymentKey());
        } catch (TossPaymentsException e) {
            if (UNKNOWN_OUTCOME_CODES.contains(e.code())) {
                return ChargeOutcome.unknown("[" + e.code() + "] " + e.getMessage());
            }
            return ChargeOutcome.failed("[" + e.code() + "] " + e.getMessage(), null);
        }
    }

    static String customerKeyFor(String userId) {
        // 토스 customerKey 규칙: 2~50자, 영문·숫자·- _ = . @ 만 허용
        String cleaned = userId.replaceAll("[^A-Za-z0-9\\-_=.@]", "");
        String key = "tpl-" + cleaned;
        return key.length() > 50 ? key.substring(0, 50) : key;
    }

    private ReentrantLock lockFor(String userId) {
        return userLocks.computeIfAbsent(userId, key -> new ReentrantLock());
    }

    private void ensureConfigured() {
        if (!tossPaymentsClient.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "결제가 아직 설정되지 않았습니다. TOSS_CLIENT_KEY와 TOSS_SECRET_KEY를 확인해 주세요.");
        }
    }

    private void requireUser(AuthenticatedUser user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Current user is not resolved.");
        }
    }

    private SubscriptionResponse toResponse(Subscription subscription, AuthenticatedUser user) {
        return SubscriptionResponse.from(subscription, planService.resolve(user, false).premium());
    }

    enum ChargeStatus { DONE, FAILED, UNKNOWN }

    record ChargeOutcome(ChargeStatus status, TossPaymentsClient.Payment payment, String failureMessage, String paymentKey) {
        static ChargeOutcome done(TossPaymentsClient.Payment payment) {
            return new ChargeOutcome(ChargeStatus.DONE, payment, null, payment.paymentKey());
        }

        static ChargeOutcome failed(String message, String paymentKey) {
            return new ChargeOutcome(ChargeStatus.FAILED, null, message, paymentKey);
        }

        static ChargeOutcome unknown(String message) {
            return new ChargeOutcome(ChargeStatus.UNKNOWN, null, message, null);
        }
    }
}
