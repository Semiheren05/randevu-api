package com.semih.randevuapi.booking;

import java.time.Instant;

/** A half-open time range [start, end): a 10:00-10:30 range and a 10:30-11:00 range do not overlap. */
public record TimeRange(Instant start, Instant end) {

    public TimeRange {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("start must be before end");
        }
    }

    public boolean overlaps(TimeRange other) {
        return start.isBefore(other.end) && other.start.isBefore(end);
    }
}
