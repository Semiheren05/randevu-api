package com.semih.randevuapi.booking.dto;

import com.semih.randevuapi.booking.Booking;
import com.semih.randevuapi.booking.BookingStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** Times are shown in the business's own time zone, the way the customer will see them on the wall clock. */
public record BookingResponse(
        Long id,
        String businessSlug,
        String businessName,
        String offeringName,
        Long staffId,
        String staffName,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String timeZone,
        BigDecimal price,
        BookingStatus status,
        String customerName,
        String note) {

    public static BookingResponse from(Booking booking) {
        ZoneId zone = booking.getBusiness().getZoneId();
        return new BookingResponse(
                booking.getId(),
                booking.getBusiness().getSlug(),
                booking.getBusiness().getName(),
                booking.getOffering().getName(),
                booking.getStaff() != null ? booking.getStaff().getId() : null,
                booking.getStaff() != null ? booking.getStaff().getName() : null,
                LocalDateTime.ofInstant(booking.getStartsAt(), zone),
                LocalDateTime.ofInstant(booking.getEndsAt(), zone),
                zone.getId(),
                booking.getPrice(),
                booking.getStatus(),
                booking.getCustomer().getDisplayName(),
                booking.getNote());
    }
}
