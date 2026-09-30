package com.teample.dto.subscription;

import com.teample.entity.PaymentRecord;

import java.time.LocalDateTime;

public record PaymentResponse(
        String id,
        String orderId,
        String orderName,
        int amount,
        String status,
        String failureMessage,
        LocalDateTime approvedAt,
        LocalDateTime createdAt
) {
    public static PaymentResponse from(PaymentRecord record) {
        return new PaymentResponse(
                record.getId(),
                record.getOrderId(),
                record.getOrderName(),
                record.getAmount() == null ? 0 : record.getAmount(),
                record.getStatus(),
                record.getFailureMessage(),
                record.getApprovedAt(),
                record.getCreatedAt()
        );
    }
}
