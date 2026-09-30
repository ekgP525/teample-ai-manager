package com.teample.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubscriptionRenewalScheduler {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionRenewalScheduler.class);

    private final SubscriptionService subscriptionService;

    /** 한 시간마다 결제일이 지난 구독을 갱신한다. */
    @Scheduled(fixedDelayString = "${app.plan.renewal-check-delay-ms:3600000}", initialDelayString = "60000")
    public void renewDueSubscriptions() {
        try {
            int renewed = subscriptionService.renewDueSubscriptions();
            if (renewed > 0) {
                log.info("구독 {}건을 갱신했습니다.", renewed);
            }
        } catch (RuntimeException e) {
            log.error("구독 갱신 중 오류", e);
        }
    }
}
