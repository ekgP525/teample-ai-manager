package com.teample.service;

import com.teample.entity.PaymentRecord;
import com.teample.entity.PlanType;
import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;
import com.teample.repository.PaymentRecordRepository;
import com.teample.repository.SubscriptionRepository;
import com.teample.service.payment.TossPaymentsClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

/**
 * 구독 결제의 DB 단계만 모아 둔 저장소. 각 메서드가 짧은 트랜잭션 하나다.
 * 토스 API 호출은 절대 여기서 하지 않는다. SubscriptionService가 트랜잭션 밖에서 호출하고 결과만 넘긴다.
 */
@Service
public class SubscriptionStore {

    static final int PERIOD_DAYS = 30;
    static final int GRACE_DAYS = 3;
    static final int MAX_FAILED_ATTEMPTS = 3;
    static final String ORDER_NAME = "팀플 AI 프리미엄 1개월";
    private static final DateTimeFormatter ORDER_PERIOD_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    private final SubscriptionRepository subscriptionRepository;
    private final PaymentRecordRepository paymentRecordRepository;
    private final PlanService planService;

    public SubscriptionStore(
            SubscriptionRepository subscriptionRepository,
            PaymentRecordRepository paymentRecordRepository,
            PlanService planService
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.paymentRecordRepository = paymentRecordRepository;
        this.planService = planService;
    }

    /** "sub-" + 구독 ID 앞 12자 + "-" + 기간 시작(yyyyMMddHHmm). 같은 기간을 다시 시도하면 같은 값이 나온다. */
    static String orderIdFor(String subscriptionId, LocalDateTime periodStart) {
        String idPart = subscriptionId.replace("-", "");
        idPart = idPart.length() > 12 ? idPart.substring(0, 12) : idPart;
        return "sub-" + idPart + "-" + periodStart.format(ORDER_PERIOD_FORMAT);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findLatest(String userId) {
        return subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<Subscription> findById(String subscriptionId) {
        return subscriptionRepository.findById(subscriptionId);
    }

    @Transactional(readOnly = true)
    public List<PaymentRecord> recentPayments(String userId) {
        return paymentRecordRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Subscription> findDue(LocalDateTime now) {
        return subscriptionRepository.findByStatusAndNextBillingAtLessThanEqual(SubscriptionStatus.ACTIVE, now);
    }

    @Transactional(readOnly = true)
    public Optional<PaymentRecord> findLatestPending(String subscriptionId) {
        return paymentRecordRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(
                subscriptionId, PaymentRecord.STATUS_PENDING);
    }

    /**
     * 발급받은 빌링키를 사용자의 구독에 반영한다.
     * 이미 유효한 기간이 남아 있고 PAST_DUE가 아니면 카드만 바꾸고 결제하지 않는다(chargeNeeded=false).
     */
    @Transactional
    public BillingKeyApplied applyBillingKey(
            String userId, String customerKey, TossPaymentsClient.BillingKey issued, int priceKrw, LocalDateTime now
    ) {
        Optional<Subscription> existing = subscriptionRepository.findFirstByUserIdOrderByCreatedAtDesc(userId);
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
            return new BillingKeyApplied(subscriptionRepository.save(subscription), false);
        }

        Subscription subscription = existing.orElseGet(() -> Subscription.builder()
                .userId(userId)
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
        // 첫 결제 결과를 모르는 채로 끝나면(PENDING) 갱신 스케줄러가 1시간 뒤 같은 orderId로 다시 시도할 수 있게 둔다.
        subscription.setNextBillingAt(now);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        return new BillingKeyApplied(subscriptionRepository.save(subscription), true);
    }

    /**
     * 토스를 부르기 전에 PENDING 결제 기록을 만든다(또는 같은 기간의 기존 기록을 재사용한다).
     * 같은 orderId가 이미 DONE이면 그대로 돌려주므로 호출자는 결제 없이 기간만 연장하면 된다.
     */
    @Transactional
    public PaymentRecord beginPayment(Subscription subscription, LocalDateTime periodStart) {
        String orderId = orderIdFor(subscription.getId(), periodStart);
        Optional<PaymentRecord> existing = paymentRecordRepository.findByOrderId(orderId);
        if (existing.isPresent()) {
            PaymentRecord record = existing.get();
            if (PaymentRecord.STATUS_DONE.equals(record.getStatus())) {
                return record;
            }
            record.setStatus(PaymentRecord.STATUS_PENDING);
            record.setFailureMessage(null);
            record.setAmount(subscription.getAmount());
            record.setPeriodStart(periodStart);
            return paymentRecordRepository.save(record);
        }
        return paymentRecordRepository.save(PaymentRecord.builder()
                .subscriptionId(subscription.getId())
                .userId(subscription.getUserId())
                .orderId(orderId)
                .orderName(ORDER_NAME)
                .amount(subscription.getAmount())
                .status(PaymentRecord.STATUS_PENDING)
                .periodStart(periodStart)
                .build());
    }

    @Transactional
    public PaymentRecord markDone(PaymentRecord record, TossPaymentsClient.Payment payment) {
        PaymentRecord target = reload(record);
        target.setStatus(PaymentRecord.STATUS_DONE);
        target.setPaymentKey(payment.paymentKey());
        target.setApprovedAt(payment.approvedAt() == null ? LocalDateTime.now() : payment.approvedAt());
        target.setFailureMessage(null);
        return paymentRecordRepository.save(target);
    }

    @Transactional
    public PaymentRecord markFailed(PaymentRecord record, String failureMessage, String paymentKey) {
        PaymentRecord target = reload(record);
        target.setStatus(PaymentRecord.STATUS_FAILED);
        target.setFailureMessage(truncate(failureMessage, 1000));
        if (paymentKey != null && !paymentKey.isBlank()) {
            target.setPaymentKey(paymentKey);
        }
        return paymentRecordRepository.save(target);
    }

    /** 결제 성공: 기간을 30일 늘리고, 요금제 만료일을 기간 종료 + 유예 3일로 "늘리기만" 한다. */
    @Transactional
    public Subscription extendPeriod(String subscriptionId, LocalDateTime from) {
        Subscription subscription = requireSubscription(subscriptionId);
        LocalDateTime end = from.plusDays(PERIOD_DAYS);
        subscription.setCurrentPeriodStart(from);
        subscription.setCurrentPeriodEnd(end);
        subscription.setNextBillingAt(end);
        subscription.setFailedAttempts(0);
        subscription.setLastError(null);
        subscription.setStatus(SubscriptionStatus.ACTIVE);
        Subscription saved = subscriptionRepository.save(subscription);
        planService.grantAtLeast(saved.getUserId(), PlanType.PREMIUM, end.plusDays(GRACE_DAYS),
                "구독 결제 (" + saved.getCardCompany() + ")", PlanService.SUBSCRIPTION_GRANTER);
        return saved;
    }

    /** 첫 결제 실패: 구독을 PAST_DUE로 남겨 사용자가 카드를 다시 등록할 수 있게 한다. */
    @Transactional
    public Subscription markPastDue(String subscriptionId, String lastError) {
        Subscription subscription = requireSubscription(subscriptionId);
        subscription.setStatus(SubscriptionStatus.PAST_DUE);
        subscription.setNextBillingAt(null);
        subscription.setLastError(truncate(lastError, 1000));
        return subscriptionRepository.save(subscription);
    }

    /** 자동결제 실패: 실패 횟수를 올리고 3회째면 PAST_DUE, 아니면 하루 뒤 재시도. */
    @Transactional
    public Subscription recordRenewalFailure(String subscriptionId, String lastError, LocalDateTime now) {
        Subscription subscription = requireSubscription(subscriptionId);
        int attempts = (subscription.getFailedAttempts() == null ? 0 : subscription.getFailedAttempts()) + 1;
        subscription.setFailedAttempts(attempts);
        subscription.setLastError(truncate(lastError, 1000));
        if (attempts >= MAX_FAILED_ATTEMPTS) {
            subscription.setStatus(SubscriptionStatus.PAST_DUE);
            subscription.setNextBillingAt(null);
        } else {
            subscription.setNextBillingAt(now.plusDays(1));
        }
        return subscriptionRepository.save(subscription);
    }

    @Transactional
    public Subscription cancel(String subscriptionId, LocalDateTime now) {
        Subscription subscription = requireSubscription(subscriptionId);
        if (subscription.getStatus() == SubscriptionStatus.CANCELED) {
            return subscription;
        }
        subscription.setStatus(SubscriptionStatus.CANCELED);
        subscription.setCanceledAt(now);
        subscription.setNextBillingAt(null);
        Subscription saved = subscriptionRepository.save(subscription);
        // 남은 기간까지는 프리미엄을 유지하되 유예 기간은 주지 않는다. 관리자가 수동으로 준 요금제는 건드리지 않는다.
        planService.shortenSubscriptionGrant(saved.getUserId(), saved.getCurrentPeriodEnd(), "구독 해지 (기간 종료 시 만료)");
        return saved;
    }

    private Subscription requireSubscription(String subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalStateException("구독을 찾을 수 없습니다: " + subscriptionId));
    }

    private PaymentRecord reload(PaymentRecord record) {
        if (record.getId() == null) {
            return record;
        }
        return paymentRecordRepository.findById(record.getId()).orElse(record);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public record BillingKeyApplied(Subscription subscription, boolean chargeNeeded) {
    }
}
