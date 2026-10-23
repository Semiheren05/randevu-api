package com.semih.randevuapi.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    boolean existsByBookingId(Long bookingId);

    Page<Review> findByBusinessId(Long businessId, Pageable pageable);

    /** Average stars and number of reviews; the average is null when there are none. */
    @Query("select avg(r.rating) as average, count(r) as total from Review r where r.business.id = :businessId")
    RatingSummary summarize(@Param("businessId") Long businessId);

    interface RatingSummary {
        Double getAverage();

        Long getTotal();
    }
}
