package com.semih.randevuapi.booking;

import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    /** Confirmed bookings that overlap [from, to): the busy times of a business on a day. */
    @Query("""
            select b from Booking b
            where b.business.id = :businessId
              and b.status = com.semih.randevuapi.booking.BookingStatus.CONFIRMED
              and b.startsAt < :to and b.endsAt > :from
            """)
    List<Booking> findConfirmedOverlapping(@Param("businessId") Long businessId,
                                           @Param("from") Instant from,
                                           @Param("to") Instant to);

    @Query("""
            select b from Booking b
            where b.business.id = :businessId and b.startsAt >= :from and b.startsAt < :to
            order by b.startsAt
            """)
    List<Booking> findForBusinessBetween(@Param("businessId") Long businessId,
                                         @Param("from") Instant from,
                                         @Param("to") Instant to);

    /**
     * Confirmed bookings that start in (now, until], have not been reminded yet, and were made
     * at least {@code leadSeconds} before they start. Plain SQL, because "starts_at - created_at"
     * per row is simplest to say with a PostgreSQL interval.
     */
    @Query(value = """
            SELECT * FROM bookings
            WHERE status = 'CONFIRMED'
              AND reminder_sent_at IS NULL
              AND starts_at > :now AND starts_at <= :until
              AND starts_at - created_at >= make_interval(secs => :leadSeconds)
            ORDER BY starts_at
            """, nativeQuery = true)
    List<Booking> findDueForReminder(@Param("now") Instant now,
                                     @Param("until") Instant until,
                                     @Param("leadSeconds") long leadSeconds);

    Page<Booking> findByCustomerEmail(String customerEmail, Pageable pageable);

    Optional<Booking> findByIdAndCustomerEmail(Long id, String customerEmail);

    Optional<Booking> findByIdAndBusinessId(Long id, Long businessId);

    /** How many bookings in each status, and their total price, for a business in a period. */
    @Query("""
            select b.status as status, count(b) as total, sum(b.price) as revenue
            from Booking b
            where b.business.id = :businessId and b.startsAt >= :from and b.startsAt < :to
            group by b.status
            """)
    List<StatusTotals> totalsByStatus(@Param("businessId") Long businessId,
                                      @Param("from") Instant from,
                                      @Param("to") Instant to);

    /** The most booked services of a business in a period, confirmed bookings only. */
    @Query("""
            select o.name as name, count(b) as total
            from Booking b join b.offering o
            where b.business.id = :businessId
              and b.status = com.semih.randevuapi.booking.BookingStatus.CONFIRMED
              and b.startsAt >= :from and b.startsAt < :to
            group by o.name
            order by count(b) desc, o.name
            """)
    List<OfferingTotal> topOfferings(@Param("businessId") Long businessId,
                                     @Param("from") Instant from,
                                     @Param("to") Instant to,
                                     Limit limit);

    /** Confirmed bookings per staff member in a period; staff without bookings are not listed. */
    @Query("""
            select s.id as staffId, count(b) as total
            from Booking b join b.staff s
            where b.business.id = :businessId
              and b.status = com.semih.randevuapi.booking.BookingStatus.CONFIRMED
              and b.startsAt >= :from and b.startsAt < :to
            group by s.id
            """)
    List<StaffTotal> totalsByStaff(@Param("businessId") Long businessId,
                                   @Param("from") Instant from,
                                   @Param("to") Instant to);

    interface StatusTotals {
        BookingStatus getStatus();

        Long getTotal();

        BigDecimal getRevenue();
    }

    interface StaffTotal {
        Long getStaffId();

        Long getTotal();
    }

    interface OfferingTotal {
        String getName();

        Long getTotal();
    }
}
