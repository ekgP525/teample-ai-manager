package com.teample.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 사용자가 카카오 "나에게 보내기" 메시지 권한(talk_message)을 연결한 상태.
 * 토큰은 SecretCipher로 암호화해 저장한다.
 */
@Entity
@Table(name = "kakao_links")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KakaoLink {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "kakao_user_id", length = 64)
    private String kakaoUserId;

    @Column(name = "access_token", nullable = false, length = 2000)
    private String accessToken;

    @Column(name = "refresh_token", length = 2000)
    private String refreshToken;

    @Column(name = "access_expires_at")
    private LocalDateTime accessExpiresAt;

    @Column(name = "refresh_expires_at")
    private LocalDateTime refreshExpiresAt;

    @Column(length = 255)
    private String scopes;

    @Column(name = "deadline_reminders", nullable = false)
    private Boolean deadlineReminders;

    @Column(name = "last_notified_at")
    private LocalDateTime lastNotifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isRefreshTokenExpired(LocalDateTime now) {
        return refreshToken == null || refreshToken.isBlank()
                || (refreshExpiresAt != null && !refreshExpiresAt.isAfter(now));
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (deadlineReminders == null) {
            deadlineReminders = true;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
