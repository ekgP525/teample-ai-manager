package com.teample.entity;

public enum SubscriptionStatus {
    /** 정상 구독 중. next_billing_at에 자동 결제된다. */
    ACTIVE,
    /** 해지 신청됨. current_period_end까지는 프리미엄이 유지되고 더 이상 결제하지 않는다. */
    CANCELED,
    /** 자동 결제가 연속 실패해 결제를 멈춘 상태. 카드를 다시 등록하면 ACTIVE로 돌아간다. */
    PAST_DUE
}
