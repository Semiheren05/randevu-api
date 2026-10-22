package com.semih.randevuapi.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OfferingRepository extends JpaRepository<Offering, Long> {

    List<Offering> findByBusinessIdAndActiveTrueOrderByNameAsc(Long businessId);

    List<Offering> findByBusinessIdOrderByNameAsc(Long businessId);

    Optional<Offering> findByIdAndBusinessId(Long id, Long businessId);
}
