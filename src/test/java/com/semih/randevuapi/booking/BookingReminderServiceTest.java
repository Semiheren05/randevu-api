package com.semih.randevuapi.booking;

import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.user.Role;
import com.semih.randevuapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T07:00:00Z");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private ApplicationEventPublisher events;

    private BookingReminderService reminderService;
    private Business business;
    private Offering haircut;
    private User customer;

    @BeforeEach
    void setUp() {
        reminderService = new BookingReminderService(bookingRepository, events,
                new ReminderProperties(true, Duration.ofHours(24)), Clock.fixed(NOW, ZoneOffset.UTC));
        User owner = new User("ali@example.com", "hash", "Ali", Role.OWNER);
        customer = new User("ayse@example.com", "hash", "Ayşe", Role.CUSTOMER);
        business = new Business(owner, "kuafor-ali", "Kuaför Ali", null, "İstanbul", ZoneId.of("Europe/Istanbul"));
        haircut = new Offering(business, "Saç kesimi", 30, new BigDecimal("250.00"));
    }

    @Test
    void dueBookingsAreMarkedAndAnnounced() {
        Booking first = bookingStartingIn(Duration.ofHours(3));
        Booking second = bookingStartingIn(Duration.ofHours(20));
        // Looks 24 hours ahead, and only at bookings made at least 24 hours (86400 s) before they start
        when(bookingRepository.findDueForReminder(NOW, NOW.plus(Duration.ofHours(24)), 86_400))
                .thenReturn(List.of(first, second));

        int sent = reminderService.sendDueReminders();

        assertThat(sent).isEqualTo(2);
        assertThat(first.getReminderSentAt()).isEqualTo(NOW);
        assertThat(second.getReminderSentAt()).isEqualTo(NOW);
        ArgumentCaptor<BookingNotification> announced = ArgumentCaptor.forClass(BookingNotification.class);
        verify(events, times(2)).publishEvent(announced.capture());
        assertThat(announced.getAllValues())
                .allSatisfy(n -> assertThat(n.type()).isEqualTo(BookingNotification.Type.REMINDER));
    }

    @Test
    void nothingDueMeansNothingSent() {
        when(bookingRepository.findDueForReminder(NOW, NOW.plus(Duration.ofHours(24)), 86_400)).thenReturn(List.of());

        assertThat(reminderService.sendDueReminders()).isZero();
        verifyNoInteractions(events);
    }

    private Booking bookingStartingIn(Duration fromNow) {
        Instant start = NOW.plus(fromNow);
        return new Booking(business, haircut, customer, null, new TimeRange(start, start.plusSeconds(1800)), null);
    }
}
