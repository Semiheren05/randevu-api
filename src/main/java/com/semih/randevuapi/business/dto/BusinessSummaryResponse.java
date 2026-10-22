package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Business;

/** List view of a business, for search results. */
public record BusinessSummaryResponse(String slug, String name, String city, String description) {

    public static BusinessSummaryResponse from(Business business) {
        return new BusinessSummaryResponse(
                business.getSlug(), business.getName(), business.getCity(), business.getDescription());
    }
}
