package com.semih.randevuapi.business;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StaffRepository extends JpaRepository<Staff, Long> {

    /** Ordered by id: when several people are free, the earliest added one gets the booking. */
    List<Staff> findByBusinessIdAndActiveTrueOrderByIdAsc(Long businessId);

    List<Staff> findByBusinessIdOrderByIdAsc(Long businessId);

    Optional<Staff> findByIdAndBusinessId(Long id, Long businessId);
}
