package com.semih.randevuapi.payment;

import java.util.Optional;

/**
 * What the rest of the app needs from a payment provider. Stripe is implemented; another
 * provider such as iyzico would be one more class behind this interface, with no change
 * to bookings.
 */
public interface PaymentGateway {

    /** Short provider name stored with each payment, e.g. "stripe". */
    String name();

    /** Starts a hosted payment page; the customer is sent to {@link CheckoutSession#url()}. */
    CheckoutSession createCheckout(CheckoutRequest request);

    /** Pays the whole amount back. */
    void refund(String paymentId);

    /** The HTTP header that carries the provider's webhook signature. */
    String signatureHeader();

    /**
     * Checks that a webhook call really comes from the provider and reads it.
     *
     * @return the completed payment, or empty for events this app does not need
     * @throws InvalidWebhookException if the signature is missing, wrong or too old
     */
    Optional<CompletedPayment> parseWebhook(String payload, String signature);
}
