package com.semih.randevuapi.booking;

import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.business.Staff;
import com.semih.randevuapi.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A customer's appointment. The price is copied from the offering at booking time, so a
 * later price change does not rewrite what the customer agreed to pay.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "business_id", nullable = false, updatable = false)
    private Business business;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false, updatable = false)
    private Offering offering;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false, updatable = false)
    private User customer;

    /** Who serves the customer; null for a business without staff. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "staff_id", updatable = false)
    private Staff staff;

    @Column(name = "starts_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant endsAt;

    @Column(nullable = false, precision = 10, scale = 2, updatable = false)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "cancelled_at", columnDefinition = "timestamptz")
    private Instant cancelledAt;

    protected Booking() {
        // for JPA
    }

    public Booking(Business business, Offering offering, User customer, Staff staff, TimeRange time, String note) {
        this.business = business;
        this.offering = offering;
        this.customer = customer;
        this.staff = staff;
        this.startsAt = time.start();
        this.endsAt = time.end();
        this.price = offering.getPrice();
        this.note = note;
    }

    public void cancel(Instant now) {
        status = BookingStatus.CANCELLED;
        cancelledAt = now;
    }

    public boolean isConfirmed() {
        return status == BookingStatus.CONFIRMED;
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

    public Offering getOffering() {
        return offering;
    }

    public User getCustomer() {
        return customer;
    }

    public Staff getStaff() {
        return staff;
    }

    /** Whether this booking takes up the given person's time; bookings without staff block everyone. */
    public boolean occupies(Staff member) {
        return staff == null || staff.getId().equals(member.getId());
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCancelledAt() {
        return cancelledAt;
    }
}
