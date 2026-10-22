package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.Staff;

import java.util.List;

/**
 * @param everyService true when the person can do every service; offeringIds is then empty
 */
public record StaffResponse(Long id, String name, boolean active, boolean everyService, List<Long> offeringIds) {

    public static StaffResponse from(Staff staff) {
        return new StaffResponse(
                staff.getId(),
                staff.getName(),
                staff.isActive(),
                staff.doesEveryService(),
                staff.getOfferings().stream().map(Offering::getId).sorted().toList());
    }
}
