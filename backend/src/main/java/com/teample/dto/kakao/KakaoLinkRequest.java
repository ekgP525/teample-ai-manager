package com.teample.dto.kakao;

import jakarta.validation.constraints.NotBlank;

public record KakaoLinkRequest(@NotBlank String code, @NotBlank String redirectUri, String state) {
}
