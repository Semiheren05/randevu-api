package com.semih.randevuapi.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * @param offeringIds the services this person can do; leave empty for every service
 */
public record StaffRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 50) List<@NotNull Long> offeringIds) {
}
