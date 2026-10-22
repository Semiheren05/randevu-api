package com.semih.randevuapi.business;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BusinessRepository extends JpaRepository<Business, Long> {

    Optional<Business> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Business> findByCityIgnoreCase(String city, Pageable pageable);

    List<Business> findByOwnerEmailOrderByIdAsc(String ownerEmail);

    /** Ownership check in one query: someone else's business looks like it does not exist. */
    Optional<Business> findByIdAndOwnerEmail(Long id, String ownerEmail);
}
