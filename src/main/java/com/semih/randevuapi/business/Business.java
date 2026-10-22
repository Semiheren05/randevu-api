package com.semih.randevuapi.business;

import com.semih.randevuapi.user.User;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collection;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * A business that takes appointments: a hair salon, a clinic, a tutor... Times are stored
 * as instants (UTC) and shown in the business's own time zone.
 */
@Entity
@Table(name = "businesses")
public class Business {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private User owner;

    @Column(nullable = false, unique = true, length = 120, updatable = false)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @Column(length = 80)
    private String city;

    @Column(name = "time_zone", nullable = false, length = 60)
    private String timeZone;

    @ElementCollection
    @CollectionTable(name = "business_hours", joinColumns = @JoinColumn(name = "business_id"))
    private Set<OpeningHours> openingHours = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    protected Business() {
        // for JPA
    }

    public Business(User owner, String slug, String name, String description, String city, ZoneId timeZone) {
        this.owner = owner;
        this.slug = slug;
        this.name = name;
        this.description = description;
        this.city = city;
        this.timeZone = timeZone.getId();
    }

    /** The slug stays the same, so links people already shared keep working. */
    public void update(String name, String description, String city, ZoneId timeZone) {
        this.name = name;
        this.description = description;
        this.city = city;
        this.timeZone = timeZone.getId();
    }

    public void replaceOpeningHours(Collection<OpeningHours> hours) {
        openingHours.clear();
        openingHours.addAll(hours);
    }

    public Optional<OpeningHours> openingHoursOn(DayOfWeek day) {
        return openingHours.stream().filter(h -> h.getDayOfWeek() == day).findFirst();
    }

    public boolean isOwnedBy(String email) {
        return owner.getEmail().equals(email);
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

    public User getOwner() {
        return owner;
    }

    public String getSlug() {
        return slug;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public ZoneId getZoneId() {
        return ZoneId.of(timeZone);
    }

    public Set<OpeningHours> getOpeningHours() {
        return Set.copyOf(openingHours);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
