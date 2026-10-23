package com.semih.randevuapi.business;

import com.semih.randevuapi.business.dto.BusinessRequest;
import com.semih.randevuapi.business.dto.BusinessResponse;
import com.semih.randevuapi.business.dto.BusinessSummaryResponse;
import com.semih.randevuapi.business.dto.ClosureRequest;
import com.semih.randevuapi.business.dto.ClosureResponse;
import com.semih.randevuapi.business.dto.OfferingRequest;
import com.semih.randevuapi.business.dto.OfferingResponse;
import com.semih.randevuapi.business.dto.OpeningHoursRequest;
import com.semih.randevuapi.business.dto.StaffRequest;
import com.semih.randevuapi.business.dto.StaffResponse;
import com.semih.randevuapi.common.Slugs;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.user.User;
import com.semih.randevuapi.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class BusinessService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    /** "kuafor-ali", "kuafor-ali-2", ... gives up after this many tries. */
    private static final int MAX_SLUG_ATTEMPTS = 50;
    private static final long MAX_CLOSURE_DAYS = 366;

    private final BusinessRepository businessRepository;
    private final OfferingRepository offeringRepository;
    private final StaffRepository staffRepository;
    private final ClosureRepository closureRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public BusinessService(BusinessRepository businessRepository,
                           OfferingRepository offeringRepository,
                           StaffRepository staffRepository,
                           ClosureRepository closureRepository,
                           UserRepository userRepository,
                           Clock clock) {
        this.businessRepository = businessRepository;
        this.offeringRepository = offeringRepository;
        this.staffRepository = staffRepository;
        this.closureRepository = closureRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    // --- Public ---

    @Transactional(readOnly = true)
    public Page<BusinessSummaryResponse> search(String city, Pageable pageable) {
        Page<Business> page = city == null || city.isBlank()
                ? businessRepository.findAll(pageable)
                : businessRepository.findByCityIgnoreCase(city.trim(), pageable);
        return page.map(BusinessSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public BusinessResponse getPublic(String slug) {
        Business business = findBySlug(slug);
        return BusinessResponse.from(business,
                offeringRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(business.getId()),
                staffRepository.findByBusinessIdAndActiveTrueOrderByIdAsc(business.getId()));
    }

    @Transactional(readOnly = true)
    public Business findBySlug(String slug) {
        return businessRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    // --- Owner ---

    @Transactional
    public BusinessResponse create(BusinessRequest request, String ownerEmail) {
        User owner = userRepository.getByEmail(ownerEmail);
        String name = request.name().trim();
        Business business = businessRepository.save(new Business(
                owner, uniqueSlug(name), name, trimToNull(request.description()), trimToNull(request.city()),
                zone(request.timeZone())));
        return BusinessResponse.from(business, List.of(), List.of());
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listOwned(String ownerEmail) {
        return businessRepository.findByOwnerEmailOrderByIdAsc(ownerEmail).stream()
                .map(this::ownerView)
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponse getOwned(Long businessId, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        return ownerView(business);
    }

    @Transactional
    public BusinessResponse update(Long businessId, BusinessRequest request, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        business.update(request.name().trim(), trimToNull(request.description()), trimToNull(request.city()),
                zone(request.timeZone()));
        return ownerView(business);
    }

    /** Replaces the whole week: days not in the list become closed days. */
    @Transactional
    public BusinessResponse replaceOpeningHours(Long businessId, List<OpeningHoursRequest> days, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        business.replaceOpeningHours(toWeek(days));
        return ownerView(business);
    }

    @Transactional
    public OfferingResponse addOffering(Long businessId, OfferingRequest request, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        Offering offering = offeringRepository.save(
                new Offering(business, request.name().trim(), request.durationMinutes(), request.price()));
        return OfferingResponse.from(offering);
    }

    @Transactional
    public OfferingResponse updateOffering(Long businessId, Long offeringId, OfferingRequest request,
                                           String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        Offering offering = findOffering(businessId, offeringId);
        offering.update(request.name().trim(), request.durationMinutes(), request.price());
        return OfferingResponse.from(offering);
    }

    /** Deactivates instead of deleting: past bookings still refer to the offering. */
    @Transactional
    public void deactivateOffering(Long businessId, Long offeringId, String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        findOffering(businessId, offeringId).deactivate();
    }

    // --- Owner: staff ---

    @Transactional(readOnly = true)
    public List<StaffResponse> listStaff(Long businessId, String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        return staffRepository.findByBusinessIdOrderByIdAsc(businessId).stream().map(StaffResponse::from).toList();
    }

    @Transactional
    public StaffResponse addStaff(Long businessId, StaffRequest request, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        Staff staff = staffRepository.save(
                new Staff(business, request.name().trim(), offeringsOf(businessId, request.offeringIds())));
        return StaffResponse.from(staff);
    }

    @Transactional
    public StaffResponse updateStaff(Long businessId, Long staffId, StaffRequest request, String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        Staff staff = findStaff(businessId, staffId);
        staff.update(request.name().trim(), offeringsOf(businessId, request.offeringIds()));
        return StaffResponse.from(staff);
    }

    /**
     * Replaces a staff member's weekly hours; days left out are days off. An empty list makes
     * the person follow the business's opening hours again. Only the part inside the
     * business's opening hours can be booked.
     */
    @Transactional
    public StaffResponse replaceStaffHours(Long businessId, Long staffId, List<OpeningHoursRequest> days,
                                           String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        Staff staff = findStaff(businessId, staffId);
        staff.replaceWorkingHours(toWeek(days));
        return StaffResponse.from(staff);
    }

    /**
     * Deactivates instead of deleting: past bookings keep the person. Their upcoming
     * bookings stay as they are; the owner decides whether to cancel them.
     */
    @Transactional
    public void deactivateStaff(Long businessId, Long staffId, String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        findStaff(businessId, staffId).deactivate();
    }

    // --- Owner: holidays and days off ---

    @Transactional(readOnly = true)
    public List<ClosureResponse> listClosures(Long businessId, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        return closureRepository
                .findByBusinessIdAndEndsOnGreaterThanEqualOrderByStartsOnAscIdAsc(businessId, today(business))
                .stream()
                .map(ClosureResponse::from)
                .toList();
    }

    /**
     * Closes the business (or one staff member) on one or more days in a row. Days that
     * already have confirmed bookings cannot be closed: the owner cancels those bookings first,
     * so customers always hear about it instead of finding a closed door.
     */
    @Transactional
    public ClosureResponse addClosure(Long businessId, ClosureRequest request, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        if (request.endsOn().isBefore(request.startsOn())) {
            throw new InvalidRequestException("'endsOn' must not be before 'startsOn'");
        }
        if (request.endsOn().isBefore(today(business))) {
            throw new InvalidRequestException("A closure cannot be entirely in the past");
        }
        if (ChronoUnit.DAYS.between(request.startsOn(), request.endsOn()) >= MAX_CLOSURE_DAYS) {
            throw new InvalidRequestException("A closure can be at most " + MAX_CLOSURE_DAYS + " days long");
        }
        Staff staff = request.staffId() == null ? null : findStaff(businessId, request.staffId());

        Instant from = request.startsOn().atStartOfDay(business.getZoneId()).toInstant();
        Instant to = request.endsOn().plusDays(1).atStartOfDay(business.getZoneId()).toInstant();
        long bookings = staff == null
                ? closureRepository.countConfirmedBookings(businessId, from, to)
                : closureRepository.countConfirmedBookingsForStaff(businessId, staff.getId(), from, to);
        if (bookings > 0) {
            throw new ConflictException("Bookings in this period",
                    bookings + " confirmed booking(s) fall in this period; cancel them first");
        }
        Closure closure = closureRepository.save(new Closure(
                business, staff, request.startsOn(), request.endsOn(), trimToNull(request.reason())));
        return ClosureResponse.from(closure);
    }

    @Transactional
    public void deleteClosure(Long businessId, Long closureId, String ownerEmail) {
        requireOwned(businessId, ownerEmail);
        Closure closure = closureRepository.findByIdAndBusinessId(closureId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Closure not found"));
        closureRepository.delete(closure);
    }

    /**
     * Someone else's business answers 404, not 403, so owners cannot probe which
     * business ids exist.
     */
    @Transactional(readOnly = true)
    public Business requireOwned(Long businessId, String ownerEmail) {
        return businessRepository.findByIdAndOwnerEmail(businessId, ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
    }

    /** Checks a weekly schedule: each day at most once, and every day opens before it closes. */
    private static List<OpeningHours> toWeek(List<OpeningHoursRequest> days) {
        Set<DayOfWeek> seen = new HashSet<>();
        return days.stream()
                .map(day -> {
                    if (!seen.add(day.day())) {
                        throw new InvalidRequestException(day.day() + " is listed more than once");
                    }
                    if (!day.opensAt().isBefore(day.closesAt())) {
                        throw new InvalidRequestException(day.day() + ": opening time must be before closing time");
                    }
                    return new OpeningHours(day.day(), day.opensAt(), day.closesAt());
                })
                .toList();
    }

    private LocalDate today(Business business) {
        return LocalDate.now(clock.withZone(business.getZoneId()));
    }

    private BusinessResponse ownerView(Business business) {
        return BusinessResponse.from(business,
                offeringRepository.findByBusinessIdOrderByNameAsc(business.getId()),
                staffRepository.findByBusinessIdOrderByIdAsc(business.getId()));
    }

    private Staff findStaff(Long businessId, Long staffId) {
        return staffRepository.findByIdAndBusinessId(staffId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found"));
    }

    /** The given services, all of which must belong to this business. Empty means "every service". */
    private List<Offering> offeringsOf(Long businessId, List<Long> offeringIds) {
        if (offeringIds == null || offeringIds.isEmpty()) {
            return List.of();
        }
        Set<Long> wanted = new LinkedHashSet<>(offeringIds);
        List<Offering> found = offeringRepository.findAllById(wanted).stream()
                .filter(offering -> offering.getBusiness().getId().equals(businessId))
                .toList();
        if (found.size() != wanted.size()) {
            throw new InvalidRequestException("Unknown service id in " + wanted);
        }
        return found;
    }

    private Offering findOffering(Long businessId, Long offeringId) {
        return offeringRepository.findByIdAndBusinessId(offeringId, businessId)
                .orElseThrow(() -> new ResourceNotFoundException("Offering not found"));
    }

    private String uniqueSlug(String name) {
        String base = Slugs.from(name);
        if (base.isEmpty()) {
            throw new InvalidRequestException("The business name must contain letters or digits");
        }
        String slug = base;
        for (int attempt = 2; businessRepository.existsBySlug(slug); attempt++) {
            if (attempt > MAX_SLUG_ATTEMPTS) {
                throw new InvalidRequestException("Too many businesses with this name, please choose another");
            }
            slug = base + "-" + attempt;
        }
        return slug;
    }

    private static ZoneId zone(String timeZone) {
        if (timeZone == null || timeZone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timeZone.trim());
        } catch (DateTimeException e) {
            throw new InvalidRequestException("Unknown time zone: " + timeZone);
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
