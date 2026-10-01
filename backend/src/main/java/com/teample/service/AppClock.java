package com.teample.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 서비스 전체가 같은 시간대(서울)로 날짜를 판정하도록 한 곳에 모은다. 서버가 UTC로 돌아도 종료일·마감일이 어긋나지 않는다. */
public final class AppClock {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private AppClock() {
    }

    public static LocalDate today() {
        return LocalDate.now(ZONE);
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }
}
