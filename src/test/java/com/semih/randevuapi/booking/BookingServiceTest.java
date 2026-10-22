package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.BookingRequest;
import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.BusinessService;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.OfferingRepository;
import com.semih.randevuapi.business.OpeningHours;
import com.semih.randevuapi.common.exception.CancellationNotAllowedException;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.SlotNotAvailableException;
import com.semih.randevuapi.user.Role;
import com.semih.randevuapi.user.User;
import com.semih.randevuapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final ZoneId ISTANBUL = ZoneId.of("Europe/Istanbul");
    // Thursday 22 October 2026, 10:00 in Istanbul
    private static final Instant NOW = LocalDateTime.of(2026, 10, 22, 10, 0).atZone(ISTANBUL).toInstant();
    private static final LocalDate TOMORROW = LocalDate.of(2026, 10, 23);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private OfferingRepository offeringRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BusinessService businessService;
    @Mock
    private ApplicationEventPublisher events;

    private BookingService bookingService;
    private User customer;
    private Business business;
    private Offering haircut;

    @BeforeEach
    void setUp() {
        BookingProperties properties = new BookingProperties(Duration.ofMinutes(15), Duration.ofHours(2), 60);
        bookingService = new BookingService(bookingRepository, offeringRepository, userRepository, businessService,
                properties, events, Clock.fixed(NOW, ISTANBUL));

        User owner = new User("owner@example.com", "hash", "Ali", Role.OWNER);
        customer = new User("ayse@example.com", "hash", "Ayşe", Role.CUSTOMER);
        business = new Business(owner, "kuafor-ali", "Kuaför Ali", null, "İstanbul", ISTANBUL);
        ReflectionTestUtils.setField(business, "id", 1L);
        business.replaceOpeningHours(List.of(
                new OpeningHours(DayOfWeek.FRIDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        haircut = new Offering(business, "Saç kesimi", 30, new BigDecimal("250.00"));
        ReflectionTestUtils.setField(haircut, "id", 7L);
    }

    @Test
    void bookingAFreeSlotSavesItAndAnnouncesIt() {
        givenBookable();
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());

        var response = bookingService.book(request("2026-10-23T10:30"), "ayse@example.com");

        assertThat(response.startsAt()).isEqualTo(LocalDateTime.parse("2026-10-23T10:30"));
        assertThat(response.endsAt()).isEqualTo(LocalDateTime.parse("2026-10-23T11:00"));
        assertThat(response.price()).isEqualByComparingTo("250");
        verify(bookingRepository).saveAndFlush(any(Booking.class));
        verify(events).publishEvent(any(BookingNotification.class));
    }

    @Test
    void aTimeThatIsNotAFreeSlotIsRejected() {
        givenBookable();
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());

        // 11:45 + 30 minutes runs past closing time (12:00)
        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T11:45"), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
        verify(bookingRepository, never()).saveAndFlush(any());
    }

    @Test
    void losingARaceAtTheDatabaseIsReportedAsSlotNotAvailable() {
        givenBookable();
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());
        when(bookingRepository.saveAndFlush(any(Booking.class)))
                .thenThrow(new DataIntegrityViolationException("ex_bookings_no_overlap"));

        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30"), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
        verify(events, never()).publishEvent(any());
    }

    @Test
    void bookingTooFarAheadIsRejected() {
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));

        assertThatThrownBy(() -> bookingService.availability("kuafor-ali", 7L, TOMORROW.plusDays(90)))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void customerCanCancelWellBeforeTheAppointment() {
        Booking booking = booking("2026-10-23T10:30");
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));

        bookingService.cancelByCustomer(5L, "ayse@example.com");

        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
        assertThat(booking.getCancelledAt()).isEqualTo(NOW);
    }

    @Test
    void customerCannotCancelInsideTheNoticePeriod() {
        // Starts at 11:30 today, only 1.5 hours from now; the notice period is 2 hours
        Booking booking = booking("2026-10-22T11:30");
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelByCustomer(5L, "ayse@example.com"))
                .isInstanceOf(CancellationNotAllowedException.class);
        assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
    }

    @Test
    void aCancelledBookingCannotBeCancelledAgain() {
        Booking booking = booking("2026-10-23T10:30");
        booking.cancel(NOW);
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelByCustomer(5L, "ayse@example.com"))
                .isInstanceOf(CancellationNotAllowedException.class);
    }

    @Test
    void statsPeriodMustBeInOrder() {
        assertThatThrownBy(() -> bookingService.stats(1L, TOMORROW, TOMORROW.minusDays(1), "owner@example.com"))
                .isInstanceOf(InvalidRequestException.class);
    }

    private void givenBookable() {
        when(userRepository.getByEmail("ayse@example.com")).thenReturn(customer);
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));
    }

    private Booking booking(String localStart) {
        Instant start = LocalDateTime.parse(localStart).atZone(ISTANBUL).toInstant();
        return new Booking(business, haircut, customer, new TimeRange(start, start.plus(Duration.ofMinutes(30))), null);
    }

    private static BookingRequest request(String localStart) {
        return new BookingRequest("kuafor-ali", 7L, LocalDateTime.parse(localStart), null);
    }
}
