package com.semih.randevuapi.business;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A person who serves customers, e.g. one of the hairdressers in a salon. Each staff
 * member has their own calendar, so a salon with two hairdressers can take two
 * bookings at the same time.
 *
 * <p>Like offerings, staff are deactivated rather than deleted: past bookings keep them.
 */
@Entity
@Table(name = "staff")
public class Staff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    /** The services this person can do. Empty means every service of the business. */
    @ManyToMany
    @JoinTable(name = "staff_offerings",
            joinColumns = @JoinColumn(name = "staff_id"),
            inverseJoinColumns = @JoinColumn(name = "offering_id"))
    private Set<Offering> offerings = new HashSet<>();

    /** The person's own weekly hours. Empty means they follow the business's opening hours. */
    @ElementCollection
    @CollectionTable(name = "staff_hours", joinColumns = @JoinColumn(name = "staff_id"))
    private Set<OpeningHours> workingHours = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected Staff() {
        // for JPA
    }

    public Staff(Business business, String name, Collection<Offering> offerings) {
        this.business = business;
        this.name = name;
        this.offerings.addAll(offerings);
    }

    public void update(String name, Collection<Offering> offerings) {
        this.name = name;
        this.offerings.clear();
        this.offerings.addAll(offerings);
    }

    /** Replaces the whole week; an empty list goes back to the business's opening hours. */
    public void replaceWorkingHours(Collection<OpeningHours> hours) {
        workingHours.clear();
        workingHours.addAll(hours);
    }

    /**
     * When this person can be booked on a day: their own hours cut to the business's opening
     * hours, or simply the opening hours when they have no hours of their own.
     */
    public Optional<OpeningHours> availableHours(OpeningHours businessHours) {
        if (followsBusinessHours()) {
            return Optional.of(businessHours);
        }
        return workingHoursOn(businessHours.getDayOfWeek()).flatMap(own -> own.intersect(businessHours));
    }

    public boolean followsBusinessHours() {
        return workingHours.isEmpty();
    }

    public Optional<OpeningHours> workingHoursOn(DayOfWeek day) {
        return workingHours.stream().filter(h -> h.getDayOfWeek() == day).findFirst();
    }

    public void deactivate() {
        active = false;
    }

    /** Compared by id: the same offering can be loaded as two different objects. */
    public boolean canPerform(Offering offering) {
        return offerings.isEmpty() || offerings.stream().anyMatch(o -> o.getId().equals(offering.getId()));
    }

    public boolean doesEveryService() {
        return offerings.isEmpty();
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

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Offering> getOfferings() {
        return Set.copyOf(offerings);
    }

    public Set<OpeningHours> getWorkingHours() {
        return Set.copyOf(workingHours);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
