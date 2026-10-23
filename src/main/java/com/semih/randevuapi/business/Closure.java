package com.semih.randevuapi.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * One or more closed days in a row: a holiday for the whole business, or a day off for one
 * staff member. Unlike opening hours, which repeat every week, a closure happens once.
 */
@Entity
@Table(name = "closures")
public class Closure {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    /** Null when the whole business is closed. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", updatable = false)
    private Staff staff;

    /** First closed day, inclusive. */
    @Column(name = "starts_on", nullable = false)
    private LocalDate startsOn;

    /** Last closed day, inclusive. */
    @Column(name = "ends_on", nullable = false)
    private LocalDate endsOn;

    @Column(length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected Closure() {
        // for JPA
    }

    public Closure(Business business, Staff staff, LocalDate startsOn, LocalDate endsOn, String reason) {
        if (endsOn.isBefore(startsOn)) {
            throw new IllegalArgumentException("A closure cannot end before it starts");
        }
        this.business = business;
        this.staff = staff;
        this.startsOn = startsOn;
        this.endsOn = endsOn;
        this.reason = reason;
    }

    public boolean isWholeBusiness() {
        return staff == null;
    }

    public boolean covers(LocalDate date) {
        return !date.isBefore(startsOn) && !date.isAfter(endsOn);
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Business getBusiness() {
        return business;
    }

    public Staff getStaff() {
        return staff;
    }

    public LocalDate getStartsOn() {
        return startsOn;
    }

    public LocalDate getEndsOn() {
        return endsOn;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
