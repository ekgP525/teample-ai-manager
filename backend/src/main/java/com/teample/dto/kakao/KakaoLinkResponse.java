package com.teample.dto.kakao;

import com.teample.entity.KakaoLink;

import java.time.LocalDateTime;

public record KakaoLinkResponse(
        boolean linked,
        boolean configured,
        boolean deadlineReminders,
        boolean needsReconnect,
        LocalDateTime linkedAt,
        LocalDateTime lastNotifiedAt
) {
    public static KakaoLinkResponse notLinked(boolean configured) {
        return new KakaoLinkResponse(false, configured, false, false, null, null);
    }

    public static KakaoLinkResponse from(KakaoLink link, boolean configured, boolean needsReconnect) {
        return new KakaoLinkResponse(
                true,
                configured,
                Boolean.TRUE.equals(link.getDeadlineReminders()),
                needsReconnect,
                link.getCreatedAt(),
                link.getLastNotifiedAt()
        );
    }
}
