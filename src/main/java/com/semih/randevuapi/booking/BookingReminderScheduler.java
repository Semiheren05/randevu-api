package com.semih.randevuapi.booking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the reminder check every 15 minutes (app.reminders.check-interval).
 *
 * <p>The schedule lives in its own small class so it can be switched off
 * (app.reminders.enabled=false) while the service stays available, e.g. for tests.
 * With more than one instance of the API, every instance would run it; then a lock
 * such as ShedLock would be needed. One instance is enough for this project.
 */
@Component
@ConditionalOnProperty(prefix = "app.reminders", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BookingReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(BookingReminderScheduler.class);

    private final BookingReminderService reminderService;

    public BookingReminderScheduler(BookingReminderService reminderService) {
        this.reminderService = reminderService;
    }

    @Scheduled(fixedDelayString = "${app.reminders.check-interval:PT15M}",
            initialDelayString = "${app.reminders.initial-delay:PT1M}")
    public void sendDueReminders() {
        int sent = reminderService.sendDueReminders();
        if (sent > 0) {
            log.info("Sent {} booking reminder(s)", sent);
        }
    }
}
