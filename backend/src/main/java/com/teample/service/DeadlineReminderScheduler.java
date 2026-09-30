package com.teample.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DeadlineReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DeadlineReminderScheduler.class);

    private final DeadlineReminderService deadlineReminderService;

    /** 매일 아침 9시(서울)에 마감 알림을 보낸다. 같은 날 중복 발송은 notification_logs로 막는다. */
    @Scheduled(cron = "${app.notifications.deadline-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void sendDeadlineReminders() {
        try {
            int sent = deadlineReminderService.sendDueReminders(LocalDate.now(ZoneId.of("Asia/Seoul")));
            if (sent > 0) {
                log.info("카카오 마감 알림 {}명에게 발송", sent);
            }
        } catch (RuntimeException e) {
            log.error("마감 알림 발송 중 오류", e);
        }
    }
}
