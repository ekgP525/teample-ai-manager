package com.teample.repository;

import com.teample.entity.Subscription;
import com.teample.entity.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, String> {

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(String userId);

    List<Subscription> findByStatusAndNextBillingAtLessThanEqual(SubscriptionStatus status, LocalDateTime before);
}
