package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.OpeningHours;

import java.util.Comparator;
import java.util.List;

/**
 * Full view of a business: opening hours sorted Monday to Sunday, plus its offerings.
 * The public page shows only active offerings; the owner sees all of them.
 */
public record BusinessResponse(
        Long id,
        String slug,
        String name,
        String description,
        String city,
        String timeZone,
        List<OpeningHoursResponse> openingHours,
        List<OfferingResponse> offerings) {

    public static BusinessResponse from(Business business, List<Offering> offerings) {
        return new BusinessResponse(
                business.getId(),
                business.getSlug(),
                business.getName(),
                business.getDescription(),
                business.getCity(),
                business.getZoneId().getId(),
                business.getOpeningHours().stream()
                        .sorted(Comparator.comparing(OpeningHours::getDayOfWeek))
                        .map(OpeningHoursResponse::from)
                        .toList(),
                offerings.stream().map(OfferingResponse::from).toList());
    }
}
