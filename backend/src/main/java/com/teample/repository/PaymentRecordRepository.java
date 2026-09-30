package com.teample.repository;

import com.teample.entity.PaymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PaymentRecordRepository extends JpaRepository<PaymentRecord, String> {

    List<PaymentRecord> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
