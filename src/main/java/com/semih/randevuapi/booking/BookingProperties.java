package com.semih.randevuapi.booking;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param slotStep           free start times are offered at this step (15m: 09:00, 09:15...)
 * @param cancellationNotice customers can cancel until this long before the appointment
 * @param maxDaysAhead       how many days ahead appointments can be booked
 */
@Validated
@ConfigurationProperties(prefix = "app.booking")
public record BookingProperties(
        @NotNull Duration slotStep,
        @NotNull Duration cancellationNotice,
        @Positive int maxDaysAhead) {
}
