package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.BookingResponse;
import com.semih.randevuapi.booking.dto.StatsResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** An owner's view of their business's bookings. /api/owner/** requires ROLE_OWNER. */
@RestController
@RequestMapping("/api/owner/businesses/{businessId}")
@Tag(name = "Owner: bookings")
@SecurityRequirement(name = "bearerAuth")
public class OwnerBookingController {

    private final BookingService bookingService;

    public OwnerBookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/bookings")
    @Operation(summary = "The day's bookings in time order (?date=2026-10-23, default today; optional &staffId=3)")
    public List<BookingResponse> day(
            @PathVariable Long businessId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long staffId,
            Authentication auth) {
        return bookingService.dayForOwner(businessId, date, staffId, auth.getName());
    }

    @PostMapping("/bookings/{bookingId}/cancel")
    @Operation(summary = "Cancel an upcoming booking; the customer is emailed")
    public BookingResponse cancel(@PathVariable Long businessId, @PathVariable Long bookingId, Authentication auth) {
        return bookingService.cancelByOwner(businessId, bookingId, auth.getName());
    }

    @GetMapping("/stats")
    @Operation(summary = "Bookings, cancellations, revenue and top services in a period (?from=2026-10-01&to=2026-10-31)")
    public StatsResponse stats(
            @PathVariable Long businessId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Authentication auth) {
        return bookingService.stats(businessId, from, to, auth.getName());
    }
}
