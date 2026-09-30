package com.teample.dto.subscription;

import com.teample.entity.PlanType;
import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;

import java.time.LocalDateTime;

public record SubscriptionResponse(
        String id,
        SubscriptionStatus status,
        PlanType plan,
        int amount,
        String cardCompany,
        String cardNumber,
        LocalDateTime currentPeriodStart,
        LocalDateTime currentPeriodEnd,
        LocalDateTime nextBillingAt,
        LocalDateTime canceledAt,
        int failedAttempts,
        String lastError,
        boolean premiumActive
) {
    public static SubscriptionResponse from(Subscription subscription, boolean premiumActive) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getStatus(),
                subscription.getPlan(),
                subscription.getAmount() == null ? 0 : subscription.getAmount(),
                subscription.getCardCompany(),
                subscription.getCardNumber(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd(),
                subscription.getNextBillingAt(),
                subscription.getCanceledAt(),
                subscription.getFailedAttempts() == null ? 0 : subscription.getFailedAttempts(),
                subscription.getLastError(),
                premiumActive
        );
    }
}
