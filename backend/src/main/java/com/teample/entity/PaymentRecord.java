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

    /** 토스에 승인 요청을 보내기 직전에 만든 상태. 네트워크 오류로 결과를 모르면 이 상태로 남는다. */
    public static final String STATUS_PENDING = "PENDING";
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

    /** 이 결제가 여는 구독 기간의 시작. orderId는 (구독, 기간 시작)으로 결정된다. */
    @Column(name = "period_start")
    private LocalDateTime periodStart;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
