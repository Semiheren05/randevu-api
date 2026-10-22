package com.semih.randevuapi.business.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.semih.randevuapi.business.OpeningHours;

import java.time.DayOfWeek;
import java.time.LocalTime;

public record OpeningHoursResponse(
        DayOfWeek day,
        @JsonFormat(pattern = "HH:mm") LocalTime opensAt,
        @JsonFormat(pattern = "HH:mm") LocalTime closesAt) {

    public static OpeningHoursResponse from(OpeningHours hours) {
        return new OpeningHoursResponse(hours.getDayOfWeek(), hours.getOpensAt(), hours.getClosesAt());
    }
}
