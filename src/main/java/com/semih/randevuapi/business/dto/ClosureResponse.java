package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Closure;

import java.time.LocalDate;

/**
 * @param staffId null when the whole business is closed
 */
public record ClosureResponse(Long id, LocalDate startsOn, LocalDate endsOn, Long staffId, String staffName,
                              String reason) {

    public static ClosureResponse from(Closure closure) {
        return new ClosureResponse(
                closure.getId(),
                closure.getStartsOn(),
                closure.getEndsOn(),
                closure.isWholeBusiness() ? null : closure.getStaff().getId(),
                closure.isWholeBusiness() ? null : closure.getStaff().getName(),
                closure.getReason());
    }
}
