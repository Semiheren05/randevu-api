package com.semih.randevuapi.business;

import com.semih.randevuapi.business.dto.BusinessResponse;
import com.semih.randevuapi.business.dto.BusinessSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Public, read-only: anyone can browse businesses before booking. */
@RestController
@RequestMapping("/api/businesses")
@Tag(name = "Businesses", description = "Browse businesses and their services")
public class BusinessController {

    private final BusinessService businessService;

    public BusinessController(BusinessService businessService) {
        this.businessService = businessService;
    }

    @GetMapping
    @Operation(summary = "List businesses, optionally in one city (?city=Istanbul)")
    public Page<BusinessSummaryResponse> search(
            @RequestParam(required = false) String city,
            @PageableDefault(size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return businessService.search(city, pageable);
    }

    @GetMapping("/{slug}")
    @Operation(summary = "A business with its opening hours and active services")
    public BusinessResponse get(@PathVariable String slug) {
        return businessService.getPublic(slug);
    }
}
