package com.teample.dto.subscription;

/** 프론트가 토스페이먼츠 SDK로 카드 등록창을 열 때 필요한 값. 시크릿 키는 포함하지 않는다. */
public record CheckoutResponse(
        String clientKey,
        String customerKey,
        int amount,
        String orderName,
        String customerEmail,
        String customerName,
        boolean testMode
) {
}
