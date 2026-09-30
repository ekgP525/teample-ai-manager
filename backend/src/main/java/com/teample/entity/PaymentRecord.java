package com.teample.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 구독 결제 시도 한 건. 성공·실패 모두 남긴다. */
@Entity
@Table(name = "payment_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentRecord {

    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "subscription_id")
    private String subscriptionId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "order_id", nullable = false, length = 64)
    private String orderId;

    @Column(name = "payment_key")
    private String paymentKey;

    @Column(name = "order_name")
    private String orderName;

    @Column(nullable = false)
    private Integer amount;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "failure_message", length = 1000)
    private String failureMessage;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
