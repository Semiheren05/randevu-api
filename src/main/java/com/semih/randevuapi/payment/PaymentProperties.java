package com.semih.randevuapi.payment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param provider   none (online payment off), stripe, or fake (local development and tests)
 * @param currency   lowercase ISO code sent to the provider
 * @param successUrl where the provider sends the customer after paying
 * @param cancelUrl  where the provider sends the customer if they go back
 */
@Validated
@ConfigurationProperties(prefix = "app.payments")
public record PaymentProperties(
        @NotNull Provider provider,
        @NotBlank String currency,
        @NotBlank String successUrl,
        @NotBlank String cancelUrl,
        Stripe stripe,
        Fake fake) {

    public enum Provider {
        NONE,
        STRIPE,
        FAKE
    }

    /**
     * @param secretKey     sk_test_... for test mode
     * @param webhookSecret whsec_... of the webhook endpoint
     * @param apiBase       https://api.stripe.com (changeable for tests)
     */
    public record Stripe(String secretKey, String webhookSecret, String apiBase) {
    }

    /** @param webhookSecret value the fake provider expects in its signature header */
    public record Fake(String webhookSecret) {
    }
}
