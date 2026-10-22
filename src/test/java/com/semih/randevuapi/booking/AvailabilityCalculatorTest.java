package com.semih.randevuapi.booking;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityCalculatorTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    private static final LocalDate DAY = LocalDate.of(2026, 10, 23);
    private static final Instant LONG_AGO = Instant.parse("2026-01-01T00:00:00Z");
    private static final Duration HALF_HOUR = Duration.ofMinutes(30);
    private static final Duration QUARTER = Duration.ofMinutes(15);

    @Test
    void emptyDayOffersEveryStepWhereTheServiceFits() {
        List<LocalTime> slots = slots("09:00", "10:00", HALF_HOUR, List.of(), LONG_AGO);

        assertThat(slots).containsExactly(time("09:00"), time("09:15"), time("09:30"));
    }

    @Test
    void slotsOverlappingABookingAreLeftOut() {
        List<TimeRange> busy = List.of(range("09:30", "10:00"));

        List<LocalTime> slots = slots("09:00", "11:00", HALF_HOUR, busy, LONG_AGO);

        // 09:15 would run until 09:45 and clash; 10:00 starts right as the booking ends
        assertThat(slots).containsExactly(time("09:00"), time("10:00"), time("10:15"), time("10:30"));
    }

    @Test
    void timesInThePastAreNotOffered() {
        Instant now = at("09:20");

        assertThat(slots("09:00", "10:00", HALF_HOUR, List.of(), now)).containsExactly(time("09:30"));
    }

    @Test
    void lateEveningServiceDoesNotWrapAroundMidnight() {
        List<LocalTime> slots = slots("22:00", "23:59", Duration.ofHours(1), List.of(), LONG_AGO, Duration.ofMinutes(30));

        assertThat(slots).containsExactly(time("22:00"), time("22:30"));
    }

    @Test
    void serviceLongerThanTheOpeningHoursHasNoSlots() {
        assertThat(slots("09:00", "09:20", HALF_HOUR, List.of(), LONG_AGO)).isEmpty();
    }

    @Test
    void timeRangesThatOnlyTouchDoNotOverlap() {
        assertThat(range("10:00", "10:30").overlaps(range("10:30", "11:00"))).isFalse();
        assertThat(range("10:00", "10:30").overlaps(range("10:15", "10:45"))).isTrue();
    }

    private static List<LocalTime> slots(String opens, String closes, Duration length, List<TimeRange> busy,
                                         Instant earliest) {
        return slots(opens, closes, length, busy, earliest, QUARTER);
    }

    private static List<LocalTime> slots(String opens, String closes, Duration length, List<TimeRange> busy,
                                         Instant earliest, Duration step) {
        return AvailabilityCalculator.freeSlots(DAY, time(opens), time(closes), ISTANBUL, length, step, busy, earliest);
    }

    private static TimeRange range(String start, String end) {
        return new TimeRange(at(start), at(end));
    }

    private static Instant at(String time) {
        return DAY.atTime(time(time)).atZone(ISTANBUL).toInstant();
    }

    private static LocalTime time(String time) {
        return LocalTime.parse(time);
    }
}
