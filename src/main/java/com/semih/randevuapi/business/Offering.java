package com.semih.randevuapi.business;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * A service a business offers, e.g. "Haircut, 30 minutes, 250 TL". Named "offering" to avoid
 * confusion with Spring's {@code @Service}.
 *
 * <p>Offerings are never deleted, only deactivated: past bookings still point to them.
 */
@Entity
@Table(name = "offerings")
public class Offering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    protected Offering() {
        // for JPA
    }

    public Offering(Business business, String name, int durationMinutes, BigDecimal price) {
        this.business = business;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.price = price;
    }

    public void update(String name, int durationMinutes, BigDecimal price) {
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.price = price;
    }

    public void deactivate() {
        active = false;
    }

    public boolean belongsTo(Business other) {
        return business.getId().equals(other.getId());
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

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public Duration getDuration() {
        return Duration.ofMinutes(durationMinutes);
    }

    public BigDecimal getPrice() {
        return price;
    }

    public boolean isActive() {
        return active;
    }
}
