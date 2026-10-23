package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.AvailabilityResponse;
import com.semih.randevuapi.booking.dto.BookingRequest;
import com.semih.randevuapi.booking.dto.BookingResponse;
import com.semih.randevuapi.booking.dto.StatsResponse;
import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.BusinessService;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.OfferingRepository;
import com.semih.randevuapi.business.OpeningHours;
import com.semih.randevuapi.business.Staff;
import com.semih.randevuapi.business.StaffRepository;
import com.semih.randevuapi.common.exception.CancellationNotAllowedException;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.common.exception.SlotNotAvailableException;
import com.semih.randevuapi.user.User;
import com.semih.randevuapi.user.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Predicate;

@Service
public class BookingService {

    private static final int TOP_OFFERINGS = 5;
    private static final long MAX_STATS_DAYS = 366;
    private static final DateTimeFormatter SLOT_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepository bookingRepository;
    private final OfferingRepository offeringRepository;
    private final StaffRepository staffRepository;
    private final UserRepository userRepository;
    private final BusinessService businessService;
    private final BookingProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository,
                          OfferingRepository offeringRepository,
                          StaffRepository staffRepository,
                          UserRepository userRepository,
                          BusinessService businessService,
                          BookingProperties properties,
                          ApplicationEventPublisher events,
                          Clock clock) {
        this.bookingRepository = bookingRepository;
        this.offeringRepository = offeringRepository;
        this.staffRepository = staffRepository;
        this.userRepository = userRepository;
        this.businessService = businessService;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    // --- Public ---

    @Transactional(readOnly = true)
    public AvailabilityResponse availability(String businessSlug, Long offeringId, LocalDate date, Long staffId) {
        Business business = businessService.findBySlug(businessSlug);
        Offering offering = activeOffering(business, offeringId);
        List<String> slots = freeSlots(business, offering, date, staffId).stream()
                .map(slot -> slot.time().format(SLOT_FORMAT))
                .toList();
        return new AvailabilityResponse(date, offering.getId(), staffId, offering.getDurationMinutes(),
                business.getZoneId().getId(), slots);
    }

    // --- Customer ---

    /**
     * Books a slot. Two layers protect against double booking: the slot is checked
     * against the free slots first, and if two requests still race for the same time,
     * the database's exclusion constraints let only one of them in.
     *
     * <p>Without a chosen staff member, the work is spread evenly: of the people who are
     * free then and do the service, the one with the least booked time that day gets it
     * (on a tie, the one added first).
     */
    @Transactional
    public BookingResponse book(BookingRequest request, String customerEmail) {
        User customer = userRepository.getByEmail(customerEmail);
        Business business = businessService.findBySlug(request.businessSlug());
        Offering offering = activeOffering(business, request.offeringId());

        LocalTime time = request.startsAt().toLocalTime();
        FreeSlot slot = freeSlots(business, offering, request.startsAt().toLocalDate(), request.staffId()).stream()
                .filter(free -> free.time().equals(time))
                .findFirst()
                .orElseThrow(SlotNotAvailableException::new);

        Instant start = request.startsAt().atZone(business.getZoneId()).toInstant();
        Booking booking = new Booking(business, offering, customer, slot.staff(),
                new TimeRange(start, start.plus(offering.getDuration())), trimToNull(request.note()));
        try {
            // Flush now, so a constraint violation surfaces here and not at commit
            bookingRepository.saveAndFlush(booking);
        } catch (DataIntegrityViolationException e) {
            throw new SlotNotAvailableException();
        }
        events.publishEvent(BookingNotification.of(BookingNotification.Type.CONFIRMED, booking));
        return BookingResponse.from(booking);
    }

    @Transactional(readOnly = true)
    public Page<BookingResponse> myBookings(String customerEmail, Pageable pageable) {
        return bookingRepository.findByCustomerEmail(customerEmail, pageable).map(BookingResponse::from);
    }

    @Transactional
    public BookingResponse cancelByCustomer(Long bookingId, String customerEmail) {
        Booking booking = bookingRepository.findByIdAndCustomerEmail(bookingId, customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        Instant now = clock.instant();
        requireCancellable(booking, now);
        if (now.plus(properties.cancellationNotice()).isAfter(booking.getStartsAt())) {
            throw new CancellationNotAllowedException("Bookings can be cancelled until "
                    + properties.cancellationNotice().toMinutes() + " minutes before they start");
        }
        return cancel(booking, now);
    }

    // --- Owner ---

    @Transactional(readOnly = true)
    public List<BookingResponse> dayForOwner(Long businessId, LocalDate date, Long staffId, String ownerEmail) {
        Business business = businessService.requireOwned(businessId, ownerEmail);
        ZoneId zone = business.getZoneId();
        LocalDate day = date != null ? date : LocalDate.now(clock.withZone(zone));
        Predicate<Booking> forStaff = booking -> staffId == null
                || (booking.getStaff() != null && booking.getStaff().getId().equals(staffId));
        return bookingRepository.findForBusinessBetween(businessId, startOf(day, zone), startOf(day.plusDays(1), zone))
                .stream()
                .filter(forStaff)
                .map(BookingResponse::from)
                .toList();
    }

    /** Owners can cancel any upcoming booking, e.g. when they fall ill; there is no notice period. */
    @Transactional
    public BookingResponse cancelByOwner(Long businessId, Long bookingId, String ownerEmail) {
        businessService.requireOwned(businessId, ownerEmail);
        Booking booking = bookingRepository.findByIdAndBusinessId(bookingId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        Instant now = clock.instant();
        requireCancellable(booking, now);
        return cancel(booking, now);
    }

    /** @param to inclusive: from 1 October to 31 October covers the whole of October */
    @Transactional(readOnly = true)
    public StatsResponse stats(Long businessId, LocalDate from, LocalDate to, String ownerEmail) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'to' must not be before 'from'");
        }
        if (ChronoUnit.DAYS.between(from, to) >= MAX_STATS_DAYS) {
            throw new InvalidRequestException("The period can be at most " + MAX_STATS_DAYS + " days");
        }
        Business business = businessService.requireOwned(businessId, ownerEmail);
        Instant start = startOf(from, business.getZoneId());
        Instant end = startOf(to.plusDays(1), business.getZoneId());

        long confirmed = 0;
        long cancelled = 0;
        BigDecimal revenue = BigDecimal.ZERO;
        for (BookingRepository.StatusTotals totals : bookingRepository.totalsByStatus(businessId, start, end)) {
            if (totals.getStatus() == BookingStatus.CONFIRMED) {
                confirmed = totals.getTotal();
                revenue = totals.getRevenue();
            } else {
                cancelled = totals.getTotal();
            }
        }
        List<StatsResponse.OfferingCount> top = bookingRepository
                .topOfferings(businessId, start, end, Limit.of(TOP_OFFERINGS)).stream()
                .map(t -> new StatsResponse.OfferingCount(t.getName(), t.getTotal()))
                .toList();
        return new StatsResponse(from, to, confirmed, cancelled, revenue, top, staffCounts(businessId, start, end));
    }

    /**
     * Confirmed bookings per staff member, so the owner can see how evenly work is spread.
     * Active staff with no bookings are listed with 0; inactive ones only if they had bookings.
     */
    private List<StatsResponse.StaffCount> staffCounts(Long businessId, Instant start, Instant end) {
        Map<Long, Long> counts = new HashMap<>();
        for (BookingRepository.StaffTotal total : bookingRepository.totalsByStaff(businessId, start, end)) {
            counts.put(total.getStaffId(), total.getTotal());
        }
        return staffRepository.findByBusinessIdOrderByIdAsc(businessId).stream()
                .filter(member -> member.isActive() || counts.containsKey(member.getId()))
                .map(member -> new StatsResponse.StaffCount(member.getName(), counts.getOrDefault(member.getId(), 0L)))
                .toList();
    }

    // --- Helpers ---

    /** A free start time and who would take it; staff is null for a business without staff. */
    private record FreeSlot(LocalTime time, Staff staff) {
    }

    /**
     * Free start times on a day, sorted. A business without staff has one calendar. A
     * business with staff has one calendar per person, inside that person's own hours: a time
     * is free when at least one person who does the service (or the one asked for) is free then.
     */
    private List<FreeSlot> freeSlots(Business business, Offering offering, LocalDate date, Long staffId) {
        List<Staff> activeStaff = staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(business.getId());
        List<Staff> candidates = candidates(activeStaff, offering, staffId);

        ZoneId zone = business.getZoneId();
        LocalDate today = LocalDate.now(clock.withZone(zone));
        if (date.isBefore(today)) {
            return List.of();
        }
        if (date.isAfter(today.plusDays(properties.maxDaysAhead()))) {
            throw new InvalidRequestException("Bookings can be made at most " + properties.maxDaysAhead()
                    + " days ahead");
        }
        Optional<OpeningHours> hours = business.openingHoursOn(date.getDayOfWeek());
        if (hours.isEmpty()) {
            return List.of();
        }
        List<Booking> dayBookings = bookingRepository.findConfirmedOverlapping(
                business.getId(), startOf(date, zone), startOf(date.plusDays(1), zone));

        if (activeStaff.isEmpty()) {
            return slotsFor(date, hours.get(), zone, offering, dayBookings).stream()
                    .map(time -> new FreeSlot(time, null))
                    .toList();
        }
        // Least busy first, so putIfAbsent below gives each time to the least busy free person
        Map<Long, Long> bookedMinutes = bookedMinutesByStaff(dayBookings);
        List<Staff> leastBusyFirst = candidates.stream()
                .sorted(Comparator.comparingLong((Staff member) -> bookedMinutes.getOrDefault(member.getId(), 0L))
                        .thenComparing(Staff::getId))
                .toList();

        // TreeMap keeps the times sorted
        Map<LocalTime, Staff> assigned = new TreeMap<>();
        for (Staff member : leastBusyFirst) {
            Optional<OpeningHours> shift = member.availableHours(hours.get());
            if (shift.isEmpty()) {
                continue;
            }
            List<Booking> theirs = dayBookings.stream().filter(booking -> booking.occupies(member)).toList();
            for (LocalTime time : slotsFor(date, shift.get(), zone, offering, theirs)) {
                assigned.putIfAbsent(time, member);
            }
        }
        return assigned.entrySet().stream()
                .map(entry -> new FreeSlot(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Minutes of confirmed bookings each staff member has on the day. */
    private static Map<Long, Long> bookedMinutesByStaff(List<Booking> dayBookings) {
        Map<Long, Long> minutes = new HashMap<>();
        for (Booking booking : dayBookings) {
            if (booking.getStaff() != null) {
                long length = Duration.between(booking.getStartsAt(), booking.getEndsAt()).toMinutes();
                minutes.merge(booking.getStaff().getId(), length, Long::sum);
            }
        }
        return minutes;
    }

    /** Active staff who do the service, narrowed to one person when asked for. */
    private static List<Staff> candidates(List<Staff> activeStaff, Offering offering, Long staffId) {
        if (staffId == null) {
            return activeStaff.stream().filter(member -> member.canPerform(offering)).toList();
        }
        return activeStaff.stream()
                .filter(member -> member.getId().equals(staffId) && member.canPerform(offering))
                .findFirst()
                .map(List::of)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found for this service"));
    }

    private List<LocalTime> slotsFor(LocalDate date, OpeningHours hours, ZoneId zone, Offering offering,
                                     List<Booking> bookings) {
        List<TimeRange> busy = bookings.stream()
                .map(booking -> new TimeRange(booking.getStartsAt(), booking.getEndsAt()))
                .toList();
        return AvailabilityCalculator.freeSlots(date, hours.getOpensAt(), hours.getClosesAt(), zone,
                offering.getDuration(), properties.slotStep(), busy, clock.instant());
    }

    private Offering activeOffering(Business business, Long offeringId) {
        return offeringRepository.findByIdAndBusinessId(offeringId, business.getId())
                .filter(Offering::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Service not found"));
    }

    private static void requireCancellable(Booking booking, Instant now) {
        if (!booking.isConfirmed()) {
            throw new CancellationNotAllowedException("This booking is already cancelled");
        }
        if (!booking.getStartsAt().isAfter(now)) {
            throw new CancellationNotAllowedException("This booking has already started");
        }
    }

    private BookingResponse cancel(Booking booking, Instant now) {
        booking.cancel(now);
        events.publishEvent(BookingNotification.of(BookingNotification.Type.CANCELLED, booking));
        return BookingResponse.from(booking);
    }

    private static Instant startOf(LocalDate day, ZoneId zone) {
        return day.atStartOfDay(zone).toInstant();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
