package com.semih.randevuapi.payment;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StripeSignatureTest {

    private static final String SECRET = "whsec_test_secret";
    private static final String PAYLOAD = "{\"type\":\"checkout.session.completed\"}";
    private static final Instant NOW = Instant.parse("2026-10-23T18:00:00Z");

    @Test
    void aCorrectRecentSignatureIsAccepted() {
        String header = StripeSignature.header(PAYLOAD, SECRET, NOW.minusSeconds(30));

        assertThatCode(() -> StripeSignature.verify(PAYLOAD, header, SECRET, NOW)).doesNotThrowAnyException();
    }

    @Test
    void aChangedBodyIsRejected() {
        String header = StripeSignature.header(PAYLOAD, SECRET, NOW);

        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD.replace("completed", "expired"), header, SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessage("Signature does not match");
    }

    @Test
    void aSignatureMadeWithAnotherSecretIsRejected() {
        String header = StripeSignature.header(PAYLOAD, "whsec_someone_else", NOW);

        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD, header, SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class);
    }

    @Test
    void anOldSignatureCannotBeReplayed() {
        String header = StripeSignature.header(PAYLOAD, SECRET, NOW.minus(Duration.ofMinutes(6)));

        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD, header, SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class)
                .hasMessageContaining("tolerance");
    }

    @Test
    void anyOfSeveralV1SignaturesMayMatch() {
        // During a secret rotation Stripe sends one signature per active secret
        String valid = StripeSignature.header(PAYLOAD, SECRET, NOW);
        String header = valid + ",v1=0000";

        assertThatCode(() -> StripeSignature.verify(PAYLOAD, header, SECRET, NOW)).doesNotThrowAnyException();
    }

    @Test
    void missingOrMalformedHeadersAreRejected() {
        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD, null, SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD, "v1=abc", SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class);
        assertThatThrownBy(() -> StripeSignature.verify(PAYLOAD, "t=yesterday,v1=abc", SECRET, NOW))
                .isInstanceOf(InvalidWebhookException.class);
    }
}
