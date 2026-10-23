package com.semih.randevuapi.review.dto;

import java.util.List;

/**
 * A business's rating and one page of its reviews, newest first.
 *
 * @param averageRating rounded to one decimal, e.g. 4.3; null while there are no reviews
 */
public record BusinessReviewsResponse(
        Double averageRating,
        long reviewCount,
        List<ReviewResponse> reviews,
        int page,
        int totalPages) {
}
