package com.semih.randevuapi.business.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * @param startsOn first closed day, inclusive
 * @param endsOn   last closed day, inclusive; the same as startsOn for a single day
 * @param staffId  optional: only this staff member is off; when left out the whole business is closed
 * @param reason   optional note shown to customers, e.g. "Kurban Bayramı"
 */
public record ClosureRequest(
        @NotNull LocalDate startsOn,
        @NotNull LocalDate endsOn,
        Long staffId,
        @Size(max = 200) String reason) {
}
