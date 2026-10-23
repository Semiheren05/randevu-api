package com.semih.randevuapi.business;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Hours for one day of the week, e.g. Monday 09:00-18:00: a business's opening hours or a
 * staff member's working hours. A day with no entry is a day off. Equality is by day only:
 * there is at most one entry per day.
 */
@Embeddable
public class OpeningHours {

    @Convert(converter = DayOfWeekConverter.class)
    @Column(name = "day_of_week", nullable = false)
    private DayOfWeek dayOfWeek;

    @Column(name = "opens_at", nullable = false)
    private LocalTime opensAt;

    @Column(name = "closes_at", nullable = false)
    private LocalTime closesAt;

    protected OpeningHours() {
        // for JPA
    }

    public OpeningHours(DayOfWeek dayOfWeek, LocalTime opensAt, LocalTime closesAt) {
        if (!opensAt.isBefore(closesAt)) {
            throw new IllegalArgumentException("Opening time must be before closing time");
        }
        this.dayOfWeek = dayOfWeek;
        this.opensAt = opensAt;
        this.closesAt = closesAt;
    }

    /**
     * The part of the day both cover, e.g. a staff member working 08:00-13:00 in a salon open
     * 09:00-18:00 can be booked 09:00-13:00. Empty when they do not overlap.
     */
    public Optional<OpeningHours> intersect(OpeningHours other) {
        if (dayOfWeek != other.dayOfWeek) {
            return Optional.empty();
        }
        LocalTime start = opensAt.isAfter(other.opensAt) ? opensAt : other.opensAt;
        LocalTime end = closesAt.isBefore(other.closesAt) ? closesAt : other.closesAt;
        return start.isBefore(end) ? Optional.of(new OpeningHours(dayOfWeek, start, end)) : Optional.empty();
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getOpensAt() {
        return opensAt;
    }

    public LocalTime getClosesAt() {
        return closesAt;
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof OpeningHours other && dayOfWeek == other.dayOfWeek);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(dayOfWeek);
    }
}
