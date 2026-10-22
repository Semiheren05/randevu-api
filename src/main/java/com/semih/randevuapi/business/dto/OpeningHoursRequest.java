package com.semih.randevuapi.business.dto;

import jakarta.validation.constraints.NotNull;

import java.time.DayOfWeek;
import java.time.LocalTime;

/** One open day, e.g. {"day": "MONDAY", "opensAt": "09:00", "closesAt": "18:00"}. */
public record OpeningHoursRequest(
        @NotNull DayOfWeek day,
        @NotNull LocalTime opensAt,
        @NotNull LocalTime closesAt) {
}
