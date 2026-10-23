package com.semih.randevuapi.booking;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.time.Instant;

/**
 * The online payment of a booking, stored in the bookings table itself: a booking is paid at
 * most once, so a separate table would add a join and nothing else.
 */
@Embeddable
public class PaymentInfo {

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.UNPAID;

    @Column(name = "payment_provider", length = 20)
    private String provider;

    /** The provider's payment page id; webhooks refer to it. */
    @Column(name = "payment_checkout_id", unique = true)
    private String checkoutId;

    /** The provider's id for the money, needed to refund it. */
    @Column(name = "payment_reference")
    private String reference;

    @Column(name = "paid_at", columnDefinition = "timestamptz")
    private Instant paidAt;

    @Column(name = "refunded_at", columnDefinition = "timestamptz")
    private Instant refundedAt;

    /** A new payment page replaces an older, unused one (pages expire after a day). */
    void startCheckout(String provider, String checkoutId) {
        this.status = PaymentStatus.PENDING;
        this.provider = provider;
        this.checkoutId = checkoutId;
    }

    void markPaid(String reference, Instant now) {
        this.status = PaymentStatus.PAID;
        this.reference = reference;
        this.paidAt = now;
    }

    void markRefunded(Instant now) {
        this.status = PaymentStatus.REFUNDED;
        this.refundedAt = now;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getProvider() {
        return provider;
    }

    public String getCheckoutId() {
        return checkoutId;
    }

    public String getReference() {
        return reference;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getRefundedAt() {
        return refundedAt;
    }
}
