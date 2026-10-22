package com.semih.randevuapi.business.dto;

import com.semih.randevuapi.business.Offering;

import java.math.BigDecimal;

public record OfferingResponse(Long id, String name, int durationMinutes, BigDecimal price, boolean active) {

    public static OfferingResponse from(Offering offering) {
        return new OfferingResponse(
                offering.getId(),
                offering.getName(),
                offering.getDurationMinutes(),
                offering.getPrice(),
                offering.isActive());
    }
}
