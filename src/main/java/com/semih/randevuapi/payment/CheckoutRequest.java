package com.semih.randevuapi.payment;

/**
 * @param reference   our own id for the payment, e.g. "booking-42"
 * @param description what the customer sees on the payment page
 * @param amountMinor amount in the smallest unit (kuruş): 250.00 TL is 25000
 * @param currency    lowercase ISO code, e.g. "try"
 */
public record CheckoutRequest(String reference, String description, long amountMinor, String currency,
                              String customerEmail) {
}
