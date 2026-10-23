package com.semih.randevuapi.booking;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param enabled  whether the scheduled job runs (off in tests, which call the service directly)
 * @param leadTime how long before the appointment the reminder goes out, 24 hours by default
 */
@Validated
@ConfigurationProperties(prefix = "app.reminders")
public record ReminderProperties(boolean enabled, @NotNull Duration leadTime) {
}
