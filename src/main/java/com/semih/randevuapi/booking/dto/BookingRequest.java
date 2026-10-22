package com.semih.randevuapi.booking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * @param startsAt local time at the business, e.g. "2026-10-23T14:30", one of the free
 *                 slots returned by the availability endpoint
 */
public record BookingRequest(
        @NotBlank String businessSlug,
        @NotNull Long offeringId,
        @NotNull LocalDateTime startsAt,
        @Size(max = 500) String note) {
}
