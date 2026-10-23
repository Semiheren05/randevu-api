package com.semih.randevuapi.review;

import com.semih.randevuapi.review.dto.BusinessReviewsResponse;
import com.semih.randevuapi.review.dto.ReviewRequest;
import com.semih.randevuapi.review.dto.ReviewResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews. Writing one needs ROLE_CUSTOMER (/api/bookings/**); reading them is public
 * (GET /api/businesses/**), see SecurityConfig.
 */
@RestController
@Tag(name = "Reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PostMapping("/api/bookings/{bookingId}/review")
    @ResponseStatus(HttpStatus.CREATED)
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Rate a booking that took place (1-5 stars, once per booking)")
    public ReviewResponse create(@PathVariable Long bookingId,
                                 @Valid @RequestBody ReviewRequest request,
                                 Authentication auth) {
        return reviewService.create(bookingId, request, auth.getName());
    }

    @GetMapping("/api/businesses/{slug}/reviews")
    @Operation(summary = "A business's average rating and its reviews, newest first (?page=0&size=10)")
    public BusinessReviewsResponse forBusiness(
            @PathVariable String slug,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return reviewService.forBusiness(slug, pageable);
    }
}
