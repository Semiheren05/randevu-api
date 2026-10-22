package com.semih.randevuapi.business;

import com.semih.randevuapi.business.dto.BusinessRequest;
import com.semih.randevuapi.business.dto.BusinessResponse;
import com.semih.randevuapi.business.dto.BusinessSummaryResponse;
import com.semih.randevuapi.business.dto.OfferingRequest;
import com.semih.randevuapi.business.dto.OfferingResponse;
import com.semih.randevuapi.business.dto.OpeningHoursRequest;
import com.semih.randevuapi.common.Slugs;
import com.semih.randevuapi.common.exception.InvalidRequestException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.user.User;
import com.semih.randevuapi.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class BusinessService {

    static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Istanbul");

    /** "kuafor-ali", "kuafor-ali-2", ... gives up after this many tries. */
    private static final int MAX_SLUG_ATTEMPTS = 50;

    private final BusinessRepository businessRepository;
    private final OfferingRepository offeringRepository;
    private final UserRepository userRepository;

    public BusinessService(BusinessRepository businessRepository,
                           OfferingRepository offeringRepository,
                           UserRepository userRepository) {
        this.businessRepository = businessRepository;
        this.offeringRepository = offeringRepository;
        this.userRepository = userRepository;
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
                offeringRepository.findByBusinessIdAndActiveTrueOrderByNameAsc(business.getId()));
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
        return BusinessResponse.from(business, List.of());
    }

    @Transactional(readOnly = true)
    public List<BusinessResponse> listOwned(String ownerEmail) {
        return businessRepository.findByOwnerEmailOrderByIdAsc(ownerEmail).stream()
                .map(business -> BusinessResponse.from(business,
                        offeringRepository.findByBusinessIdOrderByNameAsc(business.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public BusinessResponse getOwned(Long businessId, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        return BusinessResponse.from(business, offeringRepository.findByBusinessIdOrderByNameAsc(businessId));
    }

    @Transactional
    public BusinessResponse update(Long businessId, BusinessRequest request, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        business.update(request.name().trim(), trimToNull(request.description()), trimToNull(request.city()),
                zone(request.timeZone()));
        return BusinessResponse.from(business, offeringRepository.findByBusinessIdOrderByNameAsc(businessId));
    }

    /** Replaces the whole week: days not in the list become closed days. */
    @Transactional
    public BusinessResponse replaceOpeningHours(Long businessId, List<OpeningHoursRequest> days, String ownerEmail) {
        Business business = requireOwned(businessId, ownerEmail);
        Set<DayOfWeek> seen = new HashSet<>();
        List<OpeningHours> hours = days.stream()
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
        business.replaceOpeningHours(hours);
        return BusinessResponse.from(business, offeringRepository.findByBusinessIdOrderByNameAsc(businessId));
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

    /**
     * Someone else's business answers 404, not 403, so owners cannot probe which
     * business ids exist.
     */
    @Transactional(readOnly = true)
    public Business requireOwned(Long businessId, String ownerEmail) {
        return businessRepository.findByIdAndOwnerEmail(businessId, ownerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Business not found"));
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
