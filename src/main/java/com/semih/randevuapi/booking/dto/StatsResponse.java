package com.semih.randevuapi.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param revenue total price of confirmed bookings in the period (past and upcoming)
 */
public record StatsResponse(
        LocalDate from,
        LocalDate to,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal revenue,
        List<OfferingCount> topOfferings) {

    public record OfferingCount(String name, long bookings) {
    }
}
