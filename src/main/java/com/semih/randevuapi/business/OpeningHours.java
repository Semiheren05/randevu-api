package com.semih.randevuapi.business;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Opening hours for one day of the week, e.g. Monday 09:00-18:00. A day with no entry is
 * a closed day. Equality is by day only: a business has at most one entry per day.
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
