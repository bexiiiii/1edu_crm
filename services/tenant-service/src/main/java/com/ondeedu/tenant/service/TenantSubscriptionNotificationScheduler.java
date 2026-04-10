package com.ondeedu.tenant.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "tenant.subscription.notifications",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class TenantSubscriptionNotificationScheduler {

    private final TenantSubscriptionNotificationService notificationService;

    @Scheduled(
            cron = "${tenant.subscription.notifications.cron:0 0 * * * *}",
            zone = "${tenant.subscription.notifications.zone:UTC}"
    )
    public void runScheduledNotifications() {
        log.debug("Running tenant subscription notification scheduler");
        notificationService.sendTrialExpiredNotifications();
        notificationService.sendUpcomingPaymentNotifications();
        notificationService.sendOverduePaymentNotifications();
    }
}
