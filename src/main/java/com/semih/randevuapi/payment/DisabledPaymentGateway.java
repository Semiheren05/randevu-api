package com.semih.randevuapi.payment;

import java.util.Optional;

/** Used when online payment is switched off (the default): customers pay at the business. */
public class DisabledPaymentGateway implements PaymentGateway {

    @Override
    public String name() {
        return "none";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        throw new PaymentsDisabledException();
    }

    /** Nothing can have been paid online, so there is never anything to refund. */
    @Override
    public void refund(String paymentId) {
        throw new PaymentsDisabledException();
    }

    @Override
    public String signatureHeader() {
        return "Stripe-Signature";
    }

    @Override
    public Optional<CompletedPayment> parseWebhook(String payload, String signature) {
        throw new InvalidWebhookException("Online payment is not enabled");
    }
}
