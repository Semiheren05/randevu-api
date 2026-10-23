package com.semih.randevuapi.booking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @param revenue total price of confirmed bookings in the period (past and upcoming)
 * @param byStaff confirmed bookings per staff member; empty for a business without staff
 */
public record StatsResponse(
        LocalDate from,
        LocalDate to,
        long confirmedBookings,
        long cancelledBookings,
        BigDecimal revenue,
        List<OfferingCount> topOfferings,
        List<StaffCount> byStaff) {

    public record OfferingCount(String name, long bookings) {
    }

    public record StaffCount(String name, long bookings) {
    }
}
