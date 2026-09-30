package com.teample.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** 같은 알림을 두 번 보내지 않기 위한 발송 기록. */
@Entity
@Table(
        name = "notification_logs",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_logs_dedupe", columnNames = {"user_id", "channel", "dedupe_key"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationLog {

    public static final String CHANNEL_KAKAO = "KAKAO";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(name = "dedupe_key", nullable = false)
    private String dedupeKey;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;
}
