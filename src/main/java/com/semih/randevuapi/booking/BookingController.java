package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.BookingRequest;
import com.semih.randevuapi.booking.dto.BookingResponse;
import com.semih.randevuapi.common.exception.SlotNotAvailableException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Customers' own bookings. /api/bookings/** requires ROLE_CUSTOMER (SecurityConfig). */
@RestController
@RequestMapping("/api/bookings")
@Tag(name = "Customer: bookings")
@SecurityRequirement(name = "bearerAuth")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Book a free slot (409 if it was just taken)")
    public BookingResponse book(@Valid @RequestBody BookingRequest request, Authentication auth) {
        try {
            return bookingService.book(request, auth.getName());
        } catch (SlotNotAvailableException e) {
            if (request.staffId() != null) {
                throw e;
            }
            // Two customers booking the same time at the same moment can both be given the least
            // busy staff member; the database lets only one of them in. The other gets one more try
            // in a new transaction, which now sees that booking and picks someone else if anyone is free.
            return bookingService.book(request, auth.getName());
        }
    }

    @GetMapping
    @Operation(summary = "My bookings, latest first")
    public Page<BookingResponse> mine(
            @PageableDefault(size = 20, sort = "startsAt", direction = Sort.Direction.DESC) Pageable pageable,
            Authentication auth) {
        return bookingService.myBookings(auth.getName(), pageable);
    }

    @PostMapping("/{bookingId}/cancel")
    @Operation(summary = "Cancel my booking (allowed until the cancellation notice, 2 hours by default)")
    public BookingResponse cancel(@PathVariable Long bookingId, Authentication auth) {
        return bookingService.cancelByCustomer(bookingId, auth.getName());
    }
}
