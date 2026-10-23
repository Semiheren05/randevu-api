package com.semih.randevuapi.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Checks Stripe's webhook signature without the Stripe SDK.
 *
 * <p>The "Stripe-Signature" header looks like {@code t=1729600000,v1=5257a869...}. Stripe signs
 * the text {@code "<t>.<raw request body>"} with HMAC-SHA256, using the endpoint's signing
 * secret (whsec_...) as the key. Anyone can send a request to the webhook URL, so without this
 * check a fake "payment succeeded" call would mark a booking as paid. The timestamp check stops
 * an old, captured request from being replayed.
 */
public final class StripeSignature {

    /** Stripe's own libraries accept events up to 5 minutes old. */
    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private StripeSignature() {
        // utility class
    }

    /** @throws InvalidWebhookException unless the header carries a valid, recent signature */
    public static void verify(String payload, String header, String secret, Instant now) {
        if (header == null || header.isBlank()) {
            throw new InvalidWebhookException("Missing signature");
        }
        Long timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String part : header.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            if (keyValue[0].equals("t")) {
                try {
                    timestamp = Long.parseLong(keyValue[1]);
                } catch (NumberFormatException e) {
                    throw new InvalidWebhookException("Malformed signature timestamp");
                }
            } else if (keyValue[0].equals("v1")) {
                signatures.add(keyValue[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            throw new InvalidWebhookException("Malformed signature header");
        }
        if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
            throw new InvalidWebhookException("Signature timestamp is outside the tolerance");
        }
        byte[] expected = hmac(secret, timestamp + "." + payload).getBytes(StandardCharsets.UTF_8);
        for (String signature : signatures) {
            // Constant-time comparison, so response timing reveals nothing about the right value
            if (MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.UTF_8))) {
                return;
            }
        }
        throw new InvalidWebhookException("Signature does not match");
    }

    /** The value Stripe would put in the header; also used by the tests to sign sample events. */
    public static String header(String payload, String secret, Instant timestamp) {
        long seconds = timestamp.getEpochSecond();
        return "t=" + seconds + ",v1=" + hmac(secret, seconds + "." + payload);
    }

    private static String hmac(String secret, String text) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
