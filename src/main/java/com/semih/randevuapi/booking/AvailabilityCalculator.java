package com.semih.randevuapi.booking;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Works out which start times are free on one day. Pure logic with no database or clock,
 * so every rule can be unit tested with plain values.
 *
 * <p>Candidate times run from opening time in {@code step}s (09:00, 09:15, 09:30...). A
 * candidate is free when the whole service fits before closing time, it is not in the
 * past, and it does not overlap a confirmed booking.
 */
public final class AvailabilityCalculator {

    private AvailabilityCalculator() {
        // utility class
    }

    public static List<LocalTime> freeSlots(LocalDate date,
                                            LocalTime opensAt,
                                            LocalTime closesAt,
                                            ZoneId zone,
                                            Duration serviceLength,
                                            Duration step,
                                            List<TimeRange> busy,
                                            Instant earliestStart) {
        List<LocalTime> slots = new ArrayList<>();
        // LocalDateTime, not LocalTime: 23:30 + 1 hour must not wrap around to 00:30
        LocalDateTime closing = date.atTime(closesAt);
        for (LocalDateTime start = date.atTime(opensAt);
             !start.plus(serviceLength).isAfter(closing);
             start = start.plus(step)) {
            Instant startInstant = start.atZone(zone).toInstant();
            TimeRange candidate = new TimeRange(startInstant, startInstant.plus(serviceLength));
            boolean inPast = startInstant.isBefore(earliestStart);
            boolean taken = busy.stream().anyMatch(candidate::overlaps);
            if (!inPast && !taken) {
                slots.add(start.toLocalTime());
            }
        }
        return slots;
    }
}
