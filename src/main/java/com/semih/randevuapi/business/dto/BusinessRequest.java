package com.semih.randevuapi.business.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param timeZone IANA zone such as "Europe/Istanbul"; defaults to Europe/Istanbul when left out
 */
public record BusinessRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 1000) String description,
        @Size(max = 80) String city,
        @Size(max = 60) String timeZone) {
}
