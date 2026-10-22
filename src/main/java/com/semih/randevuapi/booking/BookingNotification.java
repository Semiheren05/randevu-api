package com.semih.randevuapi.booking;

import java.time.LocalDateTime;

/**
 * Published when a booking is made or cancelled. The email notifier listens to this after
 * the transaction commits, so no email goes out for a booking that was rolled back.
 * Holds plain values, not entities, because it is used on another thread.
 */
public record BookingNotification(
        Type type,
        Long bookingId,
        String customerName,
        String customerEmail,
        String ownerEmail,
        String businessName,
        String offeringName,
        LocalDateTime startsAt) {

    public enum Type {
        CONFIRMED,
        CANCELLED
    }

    static BookingNotification of(Type type, Booking booking) {
        return new BookingNotification(
                type,
                booking.getId(),
                booking.getCustomer().getDisplayName(),
                booking.getCustomer().getEmail(),
                booking.getBusiness().getOwner().getEmail(),
                booking.getBusiness().getName(),
                booking.getOffering().getName(),
                LocalDateTime.ofInstant(booking.getStartsAt(), booking.getBusiness().getZoneId()));
    }
}
