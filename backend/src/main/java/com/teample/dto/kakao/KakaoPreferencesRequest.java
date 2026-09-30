package com.teample.dto.kakao;

import jakarta.validation.constraints.NotNull;

public record KakaoPreferencesRequest(@NotNull Boolean deadlineReminders) {
}
