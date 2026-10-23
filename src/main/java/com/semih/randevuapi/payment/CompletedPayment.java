package com.semih.randevuapi.payment;

/**
 * @param checkoutId the payment page that was paid ({@link CheckoutSession#id()})
 * @param paymentId  the provider's id for the money itself, needed for refunds
 */
public record CompletedPayment(String checkoutId, String paymentId) {
}
