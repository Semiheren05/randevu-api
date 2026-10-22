package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.AvailabilityResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Public: anyone can check free times before signing in to book. */
@RestController
@Tag(name = "Businesses")
public class AvailabilityController {

    private final BookingService bookingService;

    public AvailabilityController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/api/businesses/{slug}/availability")
    @Operation(summary = "Free start times for a service on a day (?offeringId=1&date=2026-10-23[&staffId=3])")
    public AvailabilityResponse availability(
            @PathVariable String slug,
            @RequestParam Long offeringId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long staffId) {
        return bookingService.availability(slug, offeringId, date, staffId);
    }
}
