package com.semih.randevuapi.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in provider for local development and tests (app.payments.provider=fake): no real
 * money, no network. Its "webhook" is a small JSON body, {"checkoutId": ..., "paymentId": ...},
 * with the configured secret in the Fake-Signature header. Never enable it in production.
 */
public class FakePaymentGateway implements PaymentGateway {

    private final String webhookSecret;
    private final ObjectMapper objectMapper;
    private final List<String> refunds = new CopyOnWriteArrayList<>();

    public FakePaymentGateway(PaymentProperties properties, ObjectMapper objectMapper) {
        PaymentProperties.Fake fake = properties.fake();
        if (fake == null || fake.webhookSecret() == null || fake.webhookSecret().isBlank()) {
            throw new IllegalStateException("Set app.payments.fake.webhook-secret to use the fake provider");
        }
        this.webhookSecret = fake.webhookSecret();
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        String id = "fake_cs_" + UUID.randomUUID();
        return new CheckoutSession(id, "https://payments.example.test/checkout/" + id);
    }

    @Override
    public void refund(String paymentId) {
        refunds.add(paymentId);
    }

    /** Payment ids refunded so far, so tests can check that a cancellation paid the money back. */
    public List<String> refunds() {
        return List.copyOf(refunds);
    }

    @Override
    public String signatureHeader() {
        return "Fake-Signature";
    }

    @Override
    public Optional<CompletedPayment> parseWebhook(String payload, String signature) {
        if (signature == null || !MessageDigest.isEqual(webhookSecret.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8))) {
            throw new InvalidWebhookException("Signature does not match");
        }
        try {
            JsonNode body = objectMapper.readTree(payload);
            return Optional.of(new CompletedPayment(body.path("checkoutId").asText(), body.path("paymentId").asText()));
        } catch (JsonProcessingException e) {
            throw new InvalidWebhookException("Webhook body is not valid JSON");
        }
    }
}
