package com.teample.service;

import com.teample.dto.subscription.CheckoutResponse;
import com.teample.dto.subscription.PaymentResponse;
import com.teample.dto.subscription.SubscriptionResponse;
import com.teample.entity.PaymentRecord;
import com.teample.entity.PlanType;
import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;
import com.teample.repository.PaymentRecordRepository;
import com.teample.repository.SubscriptionRepository;
import com.teample.security.AuthenticatedUser;
import com.teample.service.payment.TossPaymentsClient;
import com.teample.service.payment.TossPaymentsClient.TossPaymentsException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 프리미엄 월 구독. 토스페이먼츠 빌링키로 첫 결제와 매달 자동 결제를 처리하고,
 * 결제가 성공할 때마다 PlanService에 프리미엄 만료일을 갱신한다.
 */
@Service
public class SubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    private static final int PERIOD_DAYS = 30;
    private static final int GRACE_DAYS = 3;
    private static final int MAX_FAILED_ATTEMPTS = 3;
    private static final String ORDER_NAME = "팀플 AI 프리미엄 1개월";

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PlanService planService;
    private final TossPaymentsClient tossPaymentsClient;
    private final int priceKrw;

    public SubscriptionService(
            SubscriptionRepository subscriptionRepository,
            PaymentRecordRepository paymentRecordRepository,
            PlanService planService,
            TossPaymentsClient tossPaymentsClient,
            @Value("${app.plan.premium-price-krw:4900}") int priceKrw
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.planService = planService;
        this.tossPaymentsClient = tossPaymentsClient;
        this.priceKrw = priceKrw;
    }

    /** 결제창을 열기 위한 값. 시크릿 키는 절대 포함하지 않는다. */
    @Transactional(readOnly = true)
    public CheckoutResponse checkout(AuthenticatedUser user) {
        requireUser(user);
        ensureConfigured();
        return new CheckoutResponse(
                tossPaymentsClient.clientKey(),
                customerKeyFor(user.authUserId()),
                priceKrw,
                ORDER_NAME,
                user.email(),
                user.memberKey(),
                tossPaymentsClient.isTestMode()
        );
    }

    /**
     * 결제창에서 돌아온 authKey로 빌링키를 발급하고 첫 달을 결제한다.
     * 이미 구독 중이면 카드만 바꾸고 결제하지 않는다.
     */
    @Transactional
    public SubscriptionResponse confirm(AuthenticatedUser user, String authKey, String customerKey) {
        requireUser(user);
        ensureConfigured();
        if (authKey == null || authKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "authKey가 비어 있습니다.");
        }
        if (!customerKeyFor(user.authUserId()).equals(customerKey)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "customerKey가 현재 사용자와 일치하지 않습니다.");
        }

        TossPaymentsClient.BillingKey issued;
        try {
            issued = tossPaymentsClient.issueBillingKey(authKey, customerKey);
        } catch (TossPaymentsException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "카드 등록에 실패했습니다: " + e.getMessage(), e);
        }

        LocalDateTime now = LocalDateTime.now();
        Optional<Subscription> existing = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId());
        if (existing.isPresent() && existing.get().isPremiumActiveAt(now)
                && existing.get().getStatus() != SubscriptionStatus.PAST_DUE) {
            Subscription subscription = existing.get();
            subscription.setBillingKey(issued.billingKey());
            subscription.setCardCompany(issued.cardCompany());
            subscription.setCardNumber(issued.cardNumber());
            if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                subscription.setCanceledAt(null);
                subscription.setNextBillingAt(subscription.getCurrentPeriodEnd());
            }
            return toResponse(subscriptionRepository.save(subscription), user);
        }

        Subscription subscription = existing.orElseGet(() -> Subscription.builder()
                .userId(user.authUserId())
                .customerKey(customerKey)
                .plan(PlanType.PREMIUM)
                .build());
        subscription.setCustomerKey(customerKey);
        subscription.setBillingKey(issued.billingKey());
        subscription.setCardCompany(issued.cardCompany());
        subscription.setCardNumber(issued.cardNumber());
        subscription.setAmount(priceKrw);
        subscription.setPlan(PlanType.PREMIUM);
        subscription.setFailedAttempts(0);
        subscription.setLastError(null);
        subscription.setCanceledAt(null);
        if (subscription.getCurrentPeriodStart() == null) {
            subscription.setCurrentPeriodStart(now);
            subscription.setCurrentPeriodEnd(now);
        }
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);

        PaymentRecord record = charge(saved, user.email(), user.memberKey());
        if (!PaymentRecord.STATUS_DONE.equals(record.getStatus())) {
            saved.setStatus(SubscriptionStatus.PAST_DUE);
            saved.setLastError(record.getFailureMessage());
            subscriptionRepository.save(saved);
            throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                    "첫 결제에 실패했습니다: " + record.getFailureMessage());
        }
        extendPeriod(saved, now);
        return toResponse(subscriptionRepository.save(saved), user);
    }

    @Transactional
    public SubscriptionResponse cancel(AuthenticatedUser user) {
        requireUser(user);
        Subscription subscription = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "구독 내역이 없습니다."));
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return toResponse(subscription, user);
        }
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(LocalDateTime.now());
        subscription.setNextBillingAt(null);
        // 남은 기간까지는 프리미엄을 유지하되 유예 기간은 주지 않는다.
        planService.grant(user.authUserId(), PlanType.PREMIUM, subscription.getCurrentPeriodEnd(),
                "구독 해지 (기간 종료 시 만료)", "subscription");
        return toResponse(subscriptionRepository.save(subscription), user);
    }

    @Transactional(readOnly = true)
    public Optional<SubscriptionResponse> find(AuthenticatedUser user) {
        requireUser(user);
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(user.authUserId())
                .map(subscription -> toResponse(subscription, user));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> payments(AuthenticatedUser user) {
        requireUser(user);
        return paymentRecordRepository.findTop20ByUserIdOrderByCreatedAtDesc(user.authUserId()).stream()
                .map(PaymentResponse::from)
                .toList();
    }

    /** 스케줄러가 호출. 결제일이 지난 ACTIVE 구독을 갱신한다. */
    @Transactional
    public int renewDueSubscriptions() {
        if (!tossPaymentsClient.isConfigured()) {
            return 0;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> due = subscriptionRepository
                .findByStatusAndNextBillingAtLessThanEqual(SubscriptionStatus.ACTIVE, now);
        int renewed = 0;
        for (Subscription subscription : due) {
            PaymentRecord record = charge(subscription, null, null);
            if (PaymentRecord.STATUS_DONE.equals(record.getStatus())) {
                extendPeriod(subscription, subscription.getCurrentPeriodEnd().isAfter(now)
                        ? subscription.getCurrentPeriodEnd() : now);
                renewed++;
            } else {
                int attempts = subscription.getFailedAttempts() + 1;
                subscription.setFailedAttempts(attempts);
                subscription.setLastError(record.getFailureMessage());
                if (attempts >= MAX_FAILED_ATTEMPTS) {
                    subscription.setStatus(SubscriptionStatus.PAST_DUE);
                    subscription.setNextBillingAt(null);
                    log.warn("구독 자동결제 {}회 실패, 결제 중단 (user={})", attempts, subscription.getUserId());
                } else {
                    subscription.setNextBillingAt(now.plusDays(1));
                }
            }
            subscriptionRepository.save(subscription);
        }
        return renewed;
    }

    private PaymentRecord charge(Subscription subscription, String customerEmail, String customerName) {
        String orderId = "sub-" + UUID.randomUUID().toString().replace("-", "").substring(0, 24);
        PaymentRecord record = PaymentRecord.builder()
                .subscriptionId(subscription.getId())
                .userId(subscription.getUserId())
                .orderId(orderId)
                .orderName(ORDER_NAME)
                .amount(subscription.getAmount())
                .build();
        try {
            TossPaymentsClient.Payment payment = tossPaymentsClient.chargeBilling(
                    subscription.getBillingKey(),
                    new TossPaymentsClient.BillingCharge(
                            subscription.getCustomerKey(), subscription.getAmount(), orderId, ORDER_NAME,
                            customerEmail, customerName));
            record.setPaymentKey(payment.paymentKey());
            record.setApprovedAt(payment.approvedAt() == null ? LocalDateTime.now() : payment.approvedAt());
            if (payment.isDone()) {
                record.setStatus(PaymentRecord.STATUS_DONE);
            } else {
                record.setStatus(PaymentRecord.STATUS_FAILED);
                record.setFailureMessage("결제 상태가 완료가 아닙니다: " + payment.status());
            }
        } catch (TossPaymentsException e) {
            record.setStatus(PaymentRecord.STATUS_FAILED);
            record.setFailureMessage("[" + e.code() + "] " + e.getMessage());
        }
        return paymentRecordRepository.save(record);
    }

    private void extendPeriod(Subscription subscription, LocalDateTime from) {
        LocalDateTime end = from.plusDays(PERIOD_DAYS);
        subscription.setCurrentPeriodStart(from);
        subscription.setCurrentPeriodEnd(end);
        subscription.setNextBillingAt(end);
        subscription.setFailedAttempts(0);
        subscription.setLastError(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        planService.grant(subscription.getUserId(), PlanType.PREMIUM, end.plusDays(GRACE_DAYS),
                "구독 결제 (" + subscription.getCardCompany() + ")", "subscription");
    }

    static String customerKeyFor(String userId) {
        // 토스 customerKey 규칙: 2~50자, 영문·숫자·- _ = . @ 만 허용
        String cleaned = userId.replaceAll("[^A-Za-z0-9\\-_=.@]", "");
        String key = "tpl-" + cleaned;
        return key.length() > 50 ? key.substring(0, 50) : key;
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
}
