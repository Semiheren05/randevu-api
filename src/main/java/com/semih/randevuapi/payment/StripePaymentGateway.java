package com.semih.randevuapi.payment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Clock;
import java.util.Optional;

/**
 * Stripe Checkout through Stripe's REST API, with Spring's {@link RestClient} instead of the
 * Stripe SDK: three calls are all this app needs, and plain HTTP keeps them easy to read and test.
 * With an sk_test_ key everything runs in Stripe's test mode (card 4242 4242 4242 4242).
 */
public class StripePaymentGateway implements PaymentGateway {

    private final RestClient client;
    private final String webhookSecret;
    private final PaymentProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public StripePaymentGateway(PaymentProperties properties, RestClient.Builder builder, ObjectMapper objectMapper,
                                Clock clock) {
        PaymentProperties.Stripe stripe = properties.stripe();
        if (stripe == null || isBlank(stripe.secretKey()) || isBlank(stripe.webhookSecret())) {
            throw new IllegalStateException("Set STRIPE_SECRET_KEY and STRIPE_WEBHOOK_SECRET to use Stripe");
        }
        this.client = builder
                .baseUrl(isBlank(stripe.apiBase()) ? "https://api.stripe.com" : stripe.apiBase())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + stripe.secretKey())
                .build();
        this.webhookSecret = stripe.webhookSecret();
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public CheckoutSession createCheckout(CheckoutRequest request) {
        // Stripe takes form-encoded parameters; nested ones are written with brackets
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("submit_type", "book");
        form.add("locale", "tr");
        form.add("success_url", properties.successUrl());
        form.add("cancel_url", properties.cancelUrl());
        form.add("client_reference_id", request.reference());
        form.add("customer_email", request.customerEmail());
        form.add("line_items[0][quantity]", "1");
        form.add("line_items[0][price_data][currency]", request.currency());
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(request.amountMinor()));
        form.add("line_items[0][price_data][product_data][name]", request.description());
        form.add("metadata[reference]", request.reference());
        form.add("payment_intent_data[metadata][reference]", request.reference());

        JsonNode session = post("/v1/checkout/sessions", form);
        return new CheckoutSession(session.path("id").asText(), session.path("url").asText());
    }

    @Override
    public void refund(String paymentId) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("payment_intent", paymentId);
        post("/v1/refunds", form);
    }

    @Override
    public String signatureHeader() {
        return "Stripe-Signature";
    }

    @Override
    public Optional<CompletedPayment> parseWebhook(String payload, String signature) {
        StripeSignature.verify(payload, signature, webhookSecret, clock.instant());
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new InvalidWebhookException("Webhook body is not valid JSON");
        }
        if (!"checkout.session.completed".equals(event.path("type").asText())) {
            return Optional.empty();
        }
        JsonNode session = event.path("data").path("object");
        if (!"paid".equals(session.path("payment_status").asText())) {
            return Optional.empty();
        }
        return Optional.of(new CompletedPayment(session.path("id").asText(), session.path("payment_intent").asText()));
    }

    private JsonNode post(String path, MultiValueMap<String, String> form) {
        try {
            return client.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            throw new PaymentProviderException("Stripe request failed: " + path, e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
