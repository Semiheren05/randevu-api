package com.semih.randevuapi.booking.dto;

import java.time.LocalDate;
import java.util.List;

/** Free start times for one service on one day, in the business's time zone. */
public record AvailabilityResponse(
        LocalDate date,
        Long offeringId,
        // The staff member asked for, or null: then a slot is listed when anyone who does the service is free
        Long staffId,
        int durationMinutes,
        String timeZone,
        // "HH:mm", e.g. ["09:00", "09:15"]: ready to show as buttons
        List<String> slots,
        // Set when the whole business is closed that day (a holiday); slots is then empty
        String closedReason) {
}
