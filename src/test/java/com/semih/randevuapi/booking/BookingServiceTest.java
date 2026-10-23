package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.BookingRequest;
import com.semih.randevuapi.booking.dto.StatsResponse;
import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.BusinessService;
import com.semih.randevuapi.business.Closure;
import com.semih.randevuapi.business.ClosureRepository;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.OfferingRepository;
import com.semih.randevuapi.business.OpeningHours;
import com.semih.randevuapi.business.Staff;
import com.semih.randevuapi.business.StaffRepository;
import com.semih.randevuapi.common.exception.CancellationNotAllowedException;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.common.exception.SlotNotAvailableException;
import com.semih.randevuapi.user.Role;
import com.semih.randevuapi.user.User;
import com.semih.randevuapi.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
    private StaffRepository staffRepository;
    @Mock
    private ClosureRepository closureRepository;
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
        bookingService = new BookingService(bookingRepository, offeringRepository, staffRepository, closureRepository,
                userRepository, businessService, properties, events, Clock.fixed(NOW, ISTANBUL));

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

        assertThatThrownBy(() -> bookingService.availability("kuafor-ali", 7L, TOMORROW.plusDays(90), null))
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

    // --- Staff ---

    @Test
    void withTwoStaffASlotStaysFreeUntilBothAreBooked() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T10:30", mehmet)));

        bookingService.book(request("2026-10-23T10:30"), "ayse@example.com");

        assertThat(savedBooking().getStaff()).isSameAs(zeynep);
    }

    @Test
    void whenEveryoneIsBusyTheSlotIsGone() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T10:30", mehmet), booking("2026-10-23T10:30", zeynep)));

        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30"), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void staffWhoDoNotDoTheServiceAreSkipped() {
        givenBookable();
        Offering beardTrim = new Offering(business, "Sakal", 20, new BigDecimal("150.00"));
        ReflectionTestUtils.setField(beardTrim, "id", 8L);
        Staff onlyBeards = staff(21L, "Mehmet", beardTrim);
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(onlyBeards, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T10:30", zeynep)));

        // Mehmet is free at 10:30 but does not cut hair
        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30"), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void aChosenStaffMemberMustBeFreeThemselves() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T10:30", mehmet)));

        // Zeynep is free, but the customer asked for Mehmet
        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30", 21L), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    @Test
    void anUnknownStaffMemberIsNotFound() {
        givenBookable();
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(staff(21L, "Mehmet")));

        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30", 99L), "ayse@example.com"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void aBookingWithoutStaffBlocksEveryone() {
        givenBookable();
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(staff(21L, "Mehmet")));
        // Made before the business added staff
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T10:30")));

        assertThatThrownBy(() -> bookingService.book(request("2026-10-23T10:30"), "ayse@example.com"))
                .isInstanceOf(SlotNotAvailableException.class);
    }

    // --- Holidays and days off ---

    @Test
    void aClosedDayHasNoSlotsAndSaysWhy() {
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));
        when(closureRepository.findCovering(1L, TOMORROW))
                .thenReturn(List.of(new Closure(business, null, TOMORROW, TOMORROW, "Bayram")));

        var availability = bookingService.availability("kuafor-ali", 7L, TOMORROW, null);

        assertThat(availability.slots()).isEmpty();
        assertThat(availability.closedReason()).isEqualTo("Bayram");
    }

    @Test
    void anOpenDayHasNoClosedReason() {
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));

        var availability = bookingService.availability("kuafor-ali", 7L, TOMORROW, null);

        assertThat(availability.slots()).isNotEmpty();
        assertThat(availability.closedReason()).isNull();
    }

    @Test
    void aStaffMemberOnLeaveIsSkipped() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(closureRepository.findCovering(1L, TOMORROW))
                .thenReturn(List.of(new Closure(business, mehmet, TOMORROW, TOMORROW, null)));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());

        bookingService.book(request("2026-10-23T10:30"), "ayse@example.com");

        // Mehmet would get it on a tie, but he is on leave
        assertThat(savedBooking().getStaff()).isSameAs(zeynep);
    }

    // --- Even spread and staff hours ---

    @Test
    void theStaffMemberWithTheLeastBookedTimeThatDayGetsTheBooking() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any()))
                .thenReturn(List.of(booking("2026-10-23T09:00", mehmet)));

        bookingService.book(request("2026-10-23T11:00"), "ayse@example.com");

        // Both are free at 11:00; Mehmet already has 30 minutes that day, Zeynep none
        assertThat(savedBooking().getStaff()).isSameAs(zeynep);
    }

    @Test
    void bookedMinutesCountNotTheNumberOfBookings() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of(
                booking("2026-10-23T09:00", mehmet, 90),
                booking("2026-10-23T09:00", zeynep, 30),
                booking("2026-10-23T09:30", zeynep, 30)));

        bookingService.book(request("2026-10-23T11:00"), "ayse@example.com");

        // Zeynep has two bookings but 60 minutes; Mehmet has one booking of 90 minutes
        assertThat(savedBooking().getStaff()).isSameAs(zeynep);
    }

    @Test
    void onATieTheStaffMemberAddedFirstGetsTheBooking() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());

        bookingService.book(request("2026-10-23T11:00"), "ayse@example.com");

        assertThat(savedBooking().getStaff()).isSameAs(mehmet);
    }

    @Test
    void staffOwnHoursLimitTheirFreeSlots() {
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));
        Staff mehmet = staff(21L, "Mehmet");
        // The business is open 09:00-12:00 on Fridays; Mehmet works only 10:00-11:00
        mehmet.replaceWorkingHours(List.of(hours(DayOfWeek.FRIDAY, LocalTime.of(10, 0), LocalTime.of(11, 0))));
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet));

        var availability = bookingService.availability("kuafor-ali", 7L, TOMORROW, null);

        assertThat(availability.slots()).containsExactly("10:00", "10:15", "10:30");
    }

    @Test
    void ownHoursOutsideTheOpeningHoursAreCut() {
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));
        Staff mehmet = staff(21L, "Mehmet");
        mehmet.replaceWorkingHours(List.of(hours(DayOfWeek.FRIDAY, LocalTime.of(7, 0), LocalTime.of(10, 0))));
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet));

        var availability = bookingService.availability("kuafor-ali", 7L, TOMORROW, null);

        // Nothing before 09:00, when the business opens
        assertThat(availability.slots()).containsExactly("09:00", "09:15", "09:30");
    }

    @Test
    void aStaffMemberWhoIsOffThatDayIsSkipped() {
        givenBookable();
        Staff mehmet = staff(21L, "Mehmet");
        mehmet.replaceWorkingHours(List.of(hours(DayOfWeek.MONDAY, LocalTime.of(9, 0), LocalTime.of(12, 0))));
        Staff zeynep = staff(22L, "Zeynep");
        when(staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep));
        when(bookingRepository.findConfirmedOverlapping(eq(1L), any(), any())).thenReturn(List.of());

        bookingService.book(request("2026-10-23T10:30"), "ayse@example.com");

        // Friday: Mehmet only works on Mondays
        assertThat(savedBooking().getStaff()).isSameAs(zeynep);
    }

    @Test
    void statsListEachStaffMemberIncludingThoseWithNoBookings() {
        when(businessService.requireOwned(1L, "owner@example.com")).thenReturn(business);
        Staff mehmet = staff(21L, "Mehmet");
        Staff zeynep = staff(22L, "Zeynep");
        Staff formerStaff = staff(23L, "Ali");
        formerStaff.deactivate();
        when(staffRepository.findByBusinessIdOrderByIdAsc(1L)).thenReturn(List.of(mehmet, zeynep, formerStaff));
        when(bookingRepository.totalsByStaff(eq(1L), any(), any())).thenReturn(List.of(staffTotal(21L, 2)));

        var stats = bookingService.stats(1L, TOMORROW, TOMORROW, "owner@example.com");

        // Zeynep is active, so she shows with 0; Ali left and had no bookings, so he is not listed
        assertThat(stats.byStaff()).containsExactly(
                new StatsResponse.StaffCount("Mehmet", 2),
                new StatsResponse.StaffCount("Zeynep", 0));
    }

    private static OpeningHours hours(DayOfWeek day, LocalTime opens, LocalTime closes) {
        return new OpeningHours(day, opens, closes);
    }

    private static BookingRepository.StaffTotal staffTotal(Long staffId, long total) {
        return new BookingRepository.StaffTotal() {
            @Override
            public Long getStaffId() {
                return staffId;
            }

            @Override
            public Long getTotal() {
                return total;
            }
        };
    }

    private Staff staff(Long id, String name, Offering... offerings) {
        Staff staff = new Staff(business, name, List.of(offerings));
        ReflectionTestUtils.setField(staff, "id", id);
        return staff;
    }

    private Booking savedBooking() {
        ArgumentCaptor<Booking> saved = ArgumentCaptor.forClass(Booking.class);
        verify(bookingRepository).saveAndFlush(saved.capture());
        return saved.getValue();
    }

    private void givenBookable() {
        when(userRepository.getByEmail("ayse@example.com")).thenReturn(customer);
        when(businessService.findBySlug("kuafor-ali")).thenReturn(business);
        when(offeringRepository.findByIdAndBusinessId(7L, 1L)).thenReturn(Optional.of(haircut));
    }

    private Booking booking(String localStart) {
        return booking(localStart, null);
    }

    private Booking booking(String localStart, Staff staff) {
        return booking(localStart, staff, 30);
    }

    private Booking booking(String localStart, Staff staff, int minutes) {
        Instant start = LocalDateTime.parse(localStart).atZone(ISTANBUL).toInstant();
        return new Booking(business, haircut, customer, staff,
                new TimeRange(start, start.plus(Duration.ofMinutes(minutes))), null);
    }

    private static BookingRequest request(String localStart) {
        return request(localStart, null);
    }

    private static BookingRequest request(String localStart, Long staffId) {
        return new BookingRequest("kuafor-ali", 7L, LocalDateTime.parse(localStart), staffId, null);
    }
}
