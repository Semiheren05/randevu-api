package com.semih.randevuapi.booking;

public enum PaymentStatus {
    /** Not paid online; the customer pays at the business. */
    UNPAID,
    /** A payment page was opened but no payment has arrived yet. */
    PENDING,
    PAID,
    /** Paid, then the booking was cancelled and the money returned. */
    REFUNDED
}
