package com.semih.randevuapi.business;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ClosureRepository extends JpaRepository<Closure, Long> {

    /** Current and upcoming closures, earliest first. */
    List<Closure> findByBusinessIdAndEndsOnGreaterThanEqualOrderByStartsOnAscIdAsc(Long businessId, LocalDate today);

    Optional<Closure> findByIdAndBusinessId(Long id, Long businessId);

    /** Closures that include the given day, whole-business and staff ones. */
    @Query("""
            select c from Closure c
            where c.business.id = :businessId and c.startsOn <= :date and c.endsOn >= :date
            """)
    List<Closure> findCovering(@Param("businessId") Long businessId, @Param("date") LocalDate date);

    // The two counts below check whether a new closure would hit existing bookings. They are
    // written here, in JPQL, so the business package does not need to depend on the booking package.

    @Query("""
            select count(b) from Booking b
            where b.business.id = :businessId
              and b.status = com.semih.randevuapi.booking.BookingStatus.CONFIRMED
              and b.startsAt >= :from and b.startsAt < :to
            """)
    long countConfirmedBookings(@Param("businessId") Long businessId,
                                @Param("from") Instant from,
                                @Param("to") Instant to);

    @Query("""
            select count(b) from Booking b
            where b.business.id = :businessId
              and b.staff.id = :staffId
              and b.status = com.semih.randevuapi.booking.BookingStatus.CONFIRMED
              and b.startsAt >= :from and b.startsAt < :to
            """)
    long countConfirmedBookingsForStaff(@Param("businessId") Long businessId,
                                        @Param("staffId") Long staffId,
                                        @Param("from") Instant from,
                                        @Param("to") Instant to);
}
