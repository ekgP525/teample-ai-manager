package com.teample.repository;

import com.teample.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, String> {

    List<PaymentRecord> findTop20ByUserIdOrderByCreatedAtDesc(String userId);

    Optional<PaymentRecord> findByOrderId(String orderId);

    Optional<PaymentRecord> findFirstBySubscriptionIdAndStatusOrderByCreatedAtDesc(String subscriptionId, String status);
}
