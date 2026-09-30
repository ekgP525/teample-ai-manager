package com.teample.repository;

import com.teample.entity.NotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, String> {

    boolean existsByUserIdAndChannelAndDedupeKey(String userId, String channel, String dedupeKey);
}
