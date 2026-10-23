package com.semih.randevuapi.review;

import com.semih.randevuapi.booking.Booking;
import com.semih.randevuapi.booking.BookingRepository;
import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.BusinessService;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.review.dto.BusinessReviewsResponse;
import com.semih.randevuapi.review.dto.ReviewRequest;
import com.semih.randevuapi.review.dto.ReviewResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final BookingRepository bookingRepository;
    private final BusinessService businessService;
    private final Clock clock;

    public ReviewService(ReviewRepository reviewRepository,
                         BookingRepository bookingRepository,
                         BusinessService businessService,
                         Clock clock) {
        this.reviewRepository = reviewRepository;
        this.bookingRepository = bookingRepository;
        this.businessService = businessService;
        this.clock = clock;
    }

    /**
     * Rates a booking. Allowed once, for the customer's own booking, after it has ended and
     * only if it was not cancelled: you can only review a visit that happened.
     */
    @Transactional
    public ReviewResponse create(Long bookingId, ReviewRequest request, String customerEmail) {
        Booking booking = bookingRepository.findByIdAndCustomerEmail(bookingId, customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!booking.isConfirmed()) {
            throw new ConflictException("Review not allowed", "Cancelled bookings cannot be reviewed");
        }
        if (booking.getEndsAt().isAfter(clock.instant())) {
            throw new ConflictException("Review not allowed", "A booking can be reviewed after it has taken place");
        }
        // The unique constraint on booking_id catches two requests arriving at once (409 as well)
        if (reviewRepository.existsByBookingId(bookingId)) {
            throw new ConflictException("Already reviewed", "This booking already has a review");
        }
        Review review = reviewRepository.save(new Review(booking, request.rating(), trimToNull(request.comment())));
        return ReviewResponse.from(review);
    }

    @Transactional(readOnly = true)
    public BusinessReviewsResponse forBusiness(String slug, Pageable pageable) {
        Business business = businessService.findBySlug(slug);
        ReviewRepository.RatingSummary summary = reviewRepository.summarize(business.getId());
        Page<Review> page = reviewRepository.findByBusinessId(business.getId(), pageable);
        return new BusinessReviewsResponse(
                roundToOneDecimal(summary.getAverage()),
                summary.getTotal(),
                page.map(ReviewResponse::from).getContent(),
                page.getNumber(),
                page.getTotalPages());
    }

    static Double roundToOneDecimal(Double average) {
        return average == null ? null : Math.round(average * 10) / 10.0;
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
