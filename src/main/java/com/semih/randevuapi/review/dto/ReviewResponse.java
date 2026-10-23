package com.semih.randevuapi.review.dto;

import com.semih.randevuapi.review.Review;

import java.time.Instant;

/** Shows the customer's display name only, never their email. */
public record ReviewResponse(Long id, int rating, String comment, String customerName, String offeringName,
                             Instant createdAt) {

    public static ReviewResponse from(Review review) {
        return new ReviewResponse(
                review.getId(),
                review.getRating(),
                review.getComment(),
                review.getCustomer().getDisplayName(),
                review.getBooking().getOffering().getName(),
                review.getCreatedAt());
    }
}
