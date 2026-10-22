package com.semih.randevuapi.booking.dto;

import java.time.LocalDate;
import java.util.List;

/** Free start times for one service on one day, in the business's time zone. */
public record AvailabilityResponse(
        LocalDate date,
        Long offeringId,
        int durationMinutes,
        String timeZone,
        // "HH:mm", e.g. ["09:00", "09:15"]: ready to show as buttons
        List<String> slots) {
}
