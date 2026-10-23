package com.semih.randevuapi.review;

import com.semih.randevuapi.booking.Booking;
import com.semih.randevuapi.booking.BookingRepository;
import com.semih.randevuapi.booking.TimeRange;
import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.BusinessService;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.review.dto.ReviewRequest;
import com.semih.randevuapi.user.Role;
import com.semih.randevuapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private BusinessService businessService;

    private ReviewService reviewService;
    private Business business;
    private Offering haircut;
    private User customer;

    @BeforeEach
    void setUp() {
        reviewService = new ReviewService(reviewRepository, bookingRepository, businessService,
                Clock.fixed(NOW, ZoneOffset.UTC));
        User owner = new User("ali@example.com", "hash", "Ali", Role.OWNER);
        customer = new User("ayse@example.com", "hash", "Ayşe", Role.CUSTOMER);
        business = new Business(owner, "kuafor-ali", "Kuaför Ali", null, "İstanbul", ZoneId.of("Europe/Istanbul"));
        haircut = new Offering(business, "Saç kesimi", 30, new BigDecimal("250.00"));
    }

    @Test
    void aBookingThatTookPlaceCanBeReviewed() {
        Booking past = bookingStarting(NOW.minus(Duration.ofDays(1)));
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(past));
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = reviewService.create(5L, new ReviewRequest(5, "  Çok memnun kaldım  "), "ayse@example.com");

        ArgumentCaptor<Review> saved = ArgumentCaptor.forClass(Review.class);
        verify(reviewRepository).save(saved.capture());
        assertThat(saved.getValue().getBusiness()).isSameAs(business);
        assertThat(saved.getValue().getCustomer()).isSameAs(customer);
        assertThat(response.rating()).isEqualTo(5);
        assertThat(response.comment()).isEqualTo("Çok memnun kaldım");
        assertThat(response.customerName()).isEqualTo("Ayşe");
        assertThat(response.offeringName()).isEqualTo("Saç kesimi");
    }

    @Test
    void anUpcomingBookingCannotBeReviewedYet() {
        Booking upcoming = bookingStarting(NOW.plus(Duration.ofHours(2)));
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(upcoming));

        assertThatThrownBy(() -> reviewService.create(5L, new ReviewRequest(5, null), "ayse@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("after it has taken place");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void aCancelledBookingCannotBeReviewed() {
        Booking cancelled = bookingStarting(NOW.minus(Duration.ofDays(1)));
        cancelled.cancel(NOW.minus(Duration.ofDays(2)));
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(cancelled));

        assertThatThrownBy(() -> reviewService.create(5L, new ReviewRequest(4, null), "ayse@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aBookingIsReviewedOnlyOnce() {
        Booking past = bookingStarting(NOW.minus(Duration.ofDays(1)));
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(past));
        when(reviewRepository.existsByBookingId(5L)).thenReturn(true);

        assertThatThrownBy(() -> reviewService.create(5L, new ReviewRequest(3, null), "ayse@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This booking already has a review");
    }

    @Test
    void someoneElsesBookingIsNotFound() {
        when(bookingRepository.findByIdAndCustomerEmail(5L, "deniz@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reviewService.create(5L, new ReviewRequest(1, null), "deniz@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void theAverageIsRoundedToOneDecimal() {
        assertThat(ReviewService.roundToOneDecimal(4.333)).isEqualTo(4.3);
        assertThat(ReviewService.roundToOneDecimal(4.25)).isEqualTo(4.3);
        assertThat(ReviewService.roundToOneDecimal(5.0)).isEqualTo(5.0);
        assertThat(ReviewService.roundToOneDecimal(null)).isNull();
    }

    private Booking bookingStarting(Instant start) {
        Booking booking = new Booking(business, haircut, customer, null,
                new TimeRange(start, start.plus(Duration.ofMinutes(30))), null);
        ReflectionTestUtils.setField(booking, "id", 5L);
        return booking;
    }
}
