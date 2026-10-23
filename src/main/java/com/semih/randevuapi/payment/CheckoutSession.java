package com.semih.randevuapi.payment;

/**
 * @param id  the provider's id for this payment page; the webhook refers back to it
 * @param url where to send the customer to pay
 */
public record CheckoutSession(String id, String url) {
}
