package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.OpeningHours;
import com.semih.randevuapi.business.Staff;

import java.util.Comparator;
import java.util.List;

/**
 * @param everyService         true when the person can do every service; offeringIds is then empty
 * @param followsBusinessHours true when the person has no hours of their own; workingHours is then empty
 */
public record StaffResponse(
        Long id,
        String name,
        boolean active,
        boolean everyService,
        List<Long> offeringIds,
        boolean followsBusinessHours,
        List<OpeningHoursResponse> workingHours) {

    public static StaffResponse from(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getName(),
                staff.isActive(),
                staff.doesEveryService(),
                staff.getOfferings().stream().map(Offering::getId).sorted().toList(),
                staff.followsBusinessHours(),
                staff.getWorkingHours().stream()
                        .sorted(Comparator.comparing(OpeningHours::getDayOfWeek))
                        .map(OpeningHoursResponse::from)
                        .toList());
    }
}
