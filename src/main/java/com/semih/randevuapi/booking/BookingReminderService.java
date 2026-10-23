package com.semih.randevuapi.booking;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Sends "your appointment is coming up" emails. A booking gets one reminder when it starts
 * within the lead time (24 hours by default). Bookings made less than a lead time ahead are
 * skipped: their confirmation email went out only a little while ago.
 */
@Service
public class BookingReminderService {

    private final BookingRepository bookingRepository;
    private final ApplicationEventPublisher events;
    private final ReminderProperties properties;
    private final Clock clock;

    public BookingReminderService(BookingRepository bookingRepository,
                                  ApplicationEventPublisher events,
                                  ReminderProperties properties,
                                  Clock clock) {
        this.bookingRepository = bookingRepository;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Marks each due booking as reminded and announces it; the email itself is sent after the
     * transaction commits (BookingNotifier). Marking first means a booking is never reminded twice,
     * even if the job runs again before the emails are out.
     *
     * @return how many reminders were sent
     */
    @Transactional
    public int sendDueReminders() {
        Instant now = clock.instant();
        List<Booking> due = bookingRepository.findDueForReminder(
                now, now.plus(properties.leadTime()), properties.leadTime().toSeconds());
        for (Booking booking : due) {
            booking.markReminderSent(now);
            events.publishEvent(BookingNotification.of(BookingNotification.Type.REMINDER, booking));
        }
        return due.size();
    }
}
