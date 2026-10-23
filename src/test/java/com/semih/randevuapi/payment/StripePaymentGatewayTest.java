package com.semih.randevuapi.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Checks the HTTP calls to Stripe against a fake server: no network, no Stripe account needed. */
class StripePaymentGatewayTest {

    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");
    private static final String WEBHOOK_SECRET = "whsec_test";

    private MockRestServiceServer stripe;
    private StripePaymentGateway gateway;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        stripe = MockRestServiceServer.bindTo(builder).build();
        PaymentProperties properties = new PaymentProperties(PaymentProperties.Provider.STRIPE, "try",
                "https://site.test/ok", "https://site.test/back",
                new PaymentProperties.Stripe("sk_test_123", WEBHOOK_SECRET, "https://api.stripe.test"), null);
        gateway = new StripePaymentGateway(properties, builder, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createsACheckoutSessionForTheBookingAmount() {
        stripe.expect(requestTo("https://api.stripe.test/v1/checkout/sessions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer sk_test_123"))
                .andExpect(content().formDataContains(Map.of(
                        "mode", "payment",
                        "client_reference_id", "booking-42",
                        "line_items[0][price_data][currency]", "try",
                        "line_items[0][price_data][unit_amount]", "25000",
                        "line_items[0][price_data][product_data][name]", "Kuaför Ali · Saç kesimi",
                        "success_url", "https://site.test/ok")))
                .andRespond(withSuccess("""
                        {"id": "cs_test_1", "url": "https://checkout.stripe.com/c/pay/cs_test_1"}
                        """, MediaType.APPLICATION_JSON));

        CheckoutSession session = gateway.createCheckout(
                new CheckoutRequest("booking-42", "Kuaför Ali · Saç kesimi", 25_000, "try", "ayse@example.com"));

        assertThat(session).isEqualTo(new CheckoutSession("cs_test_1", "https://checkout.stripe.com/c/pay/cs_test_1"));
        stripe.verify();
    }

    @Test
    void refundsByPaymentIntent() {
        stripe.expect(requestTo("https://api.stripe.test/v1/refunds"))
                .andExpect(content().formDataContains(Map.of("payment_intent", "pi_123")))
                .andRespond(withSuccess("{\"id\": \"re_1\", \"status\": \"succeeded\"}", MediaType.APPLICATION_JSON));

        gateway.refund("pi_123");

        stripe.verify();
    }

    @Test
    void stripeErrorsBecomeProviderErrors() {
        stripe.expect(requestTo("https://api.stripe.test/v1/refunds")).andRespond(withServerError());

        assertThatThrownBy(() -> gateway.refund("pi_123")).isInstanceOf(PaymentProviderException.class);
    }

    @Test
    void readsACompletedCheckoutFromASignedWebhook() {
        String payload = """
                {"type": "checkout.session.completed",
                 "data": {"object": {"id": "cs_test_1", "payment_intent": "pi_123", "payment_status": "paid"}}}
                """;

        assertThat(gateway.parseWebhook(payload, StripeSignature.header(payload, WEBHOOK_SECRET, NOW)))
                .contains(new CompletedPayment("cs_test_1", "pi_123"));
    }

    @Test
    void otherEventsAndUnpaidSessionsAreIgnored() {
        String expired = """
                {"type": "checkout.session.expired", "data": {"object": {"id": "cs_test_1"}}}
                """;
        String unpaid = """
                {"type": "checkout.session.completed",
                 "data": {"object": {"id": "cs_test_1", "payment_status": "unpaid"}}}
                """;

        assertThat(gateway.parseWebhook(expired, StripeSignature.header(expired, WEBHOOK_SECRET, NOW))).isEmpty();
        assertThat(gateway.parseWebhook(unpaid, StripeSignature.header(unpaid, WEBHOOK_SECRET, NOW))).isEmpty();
    }

    @Test
    void anUnsignedWebhookIsRejected() {
        assertThatThrownBy(() -> gateway.parseWebhook("{}", null)).isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void stripeNeedsItsKeys() {
        PaymentProperties withoutKeys = new PaymentProperties(PaymentProperties.Provider.STRIPE, "try",
                "https://site.test/ok", "https://site.test/back", new PaymentProperties.Stripe("", "", null), null);

        assertThatThrownBy(() -> new StripePaymentGateway(withoutKeys, RestClient.builder(), new ObjectMapper(),
                Clock.systemUTC()))
                .isInstanceOf(IllegalStateException.class);
    }
}
