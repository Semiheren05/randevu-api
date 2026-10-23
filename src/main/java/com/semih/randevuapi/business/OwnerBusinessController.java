package com.semih.randevuapi.business;

import com.semih.randevuapi.business.dto.BusinessRequest;
import com.semih.randevuapi.business.dto.BusinessResponse;
import com.semih.randevuapi.business.dto.OfferingRequest;
import com.semih.randevuapi.business.dto.OfferingResponse;
import com.semih.randevuapi.business.dto.OpeningHoursRequest;
import com.semih.randevuapi.business.dto.StaffRequest;
import com.semih.randevuapi.business.dto.StaffResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Business owners manage their own businesses here. Access to /api/owner/** requires
 * ROLE_OWNER (SecurityConfig); each call also checks that the business is the caller's.
 */
@RestController
@RequestMapping("/api/owner/businesses")
@Tag(name = "Owner: businesses")
@SecurityRequirement(name = "bearerAuth")
public class OwnerBusinessController {

    private final BusinessService businessService;

    public OwnerBusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a business; its URL slug is built from the name")
    public BusinessResponse create(@Valid @RequestBody BusinessRequest request, Authentication auth) {
        return businessService.create(request, auth.getName());
    }

    @GetMapping
    @Operation(summary = "My businesses, with all services including inactive ones")
    public List<BusinessResponse> mine(Authentication auth) {
        return businessService.listOwned(auth.getName());
    }

    @GetMapping("/{businessId}")
    @Operation(summary = "One of my businesses")
    public BusinessResponse get(@PathVariable Long businessId, Authentication auth) {
        return businessService.getOwned(businessId, auth.getName());
    }

    @PutMapping("/{businessId}")
    @Operation(summary = "Update name, description, city and time zone (the slug does not change)")
    public BusinessResponse update(@PathVariable Long businessId,
                                   @Valid @RequestBody BusinessRequest request,
                                   Authentication auth) {
        return businessService.update(businessId, request, auth.getName());
    }

    @PutMapping("/{businessId}/hours")
    @Operation(summary = "Replace the weekly opening hours; days left out are closed")
    public BusinessResponse replaceHours(@PathVariable Long businessId,
                                         @RequestBody @Size(max = 7) List<@Valid OpeningHoursRequest> days,
                                         Authentication auth) {
        return businessService.replaceOpeningHours(businessId, days, auth.getName());
    }

    @PostMapping("/{businessId}/offerings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a service, e.g. a 30-minute haircut")
    public OfferingResponse addOffering(@PathVariable Long businessId,
                                        @Valid @RequestBody OfferingRequest request,
                                        Authentication auth) {
        return businessService.addOffering(businessId, request, auth.getName());
    }

    @PutMapping("/{businessId}/offerings/{offeringId}")
    @Operation(summary = "Update a service")
    public OfferingResponse updateOffering(@PathVariable Long businessId,
                                           @PathVariable Long offeringId,
                                           @Valid @RequestBody OfferingRequest request,
                                           Authentication auth) {
        return businessService.updateOffering(businessId, offeringId, request, auth.getName());
    }

    @DeleteMapping("/{businessId}/offerings/{offeringId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a service; existing bookings keep it")
    public void deactivateOffering(@PathVariable Long businessId,
                                   @PathVariable Long offeringId,
                                   Authentication auth) {
        businessService.deactivateOffering(businessId, offeringId, auth.getName());
    }

    @GetMapping("/{businessId}/staff")
    @Operation(summary = "My staff, including inactive ones")
    public List<StaffResponse> staff(@PathVariable Long businessId, Authentication auth) {
        return businessService.listStaff(businessId, auth.getName());
    }

    @PostMapping("/{businessId}/staff")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add a staff member; with staff, each person takes their own bookings in parallel")
    public StaffResponse addStaff(@PathVariable Long businessId,
                                  @Valid @RequestBody StaffRequest request,
                                  Authentication auth) {
        return businessService.addStaff(businessId, request, auth.getName());
    }

    @PutMapping("/{businessId}/staff/{staffId}")
    @Operation(summary = "Rename a staff member or change which services they do (empty list: every service)")
    public StaffResponse updateStaff(@PathVariable Long businessId,
                                     @PathVariable Long staffId,
                                     @Valid @RequestBody StaffRequest request,
                                     Authentication auth) {
        return businessService.updateStaff(businessId, staffId, request, auth.getName());
    }

    @PutMapping("/{businessId}/staff/{staffId}/hours")
    @Operation(summary = "Replace a staff member's weekly hours (days left out are off; [] follows the business)")
    public StaffResponse replaceStaffHours(@PathVariable Long businessId,
                                           @PathVariable Long staffId,
                                           @RequestBody @Size(max = 7) List<@Valid OpeningHoursRequest> days,
                                           Authentication auth) {
        return businessService.replaceStaffHours(businessId, staffId, days, auth.getName());
    }

    @DeleteMapping("/{businessId}/staff/{staffId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Deactivate a staff member; they take no new bookings, existing ones stay")
    public void deactivateStaff(@PathVariable Long businessId, @PathVariable Long staffId, Authentication auth) {
        businessService.deactivateStaff(businessId, staffId, auth.getName());
    }
}
