package com.semih.randevuapi.payment;

/** A webhook call that cannot be trusted or read (answered with 400, so the provider retries or alerts). */
public class InvalidWebhookException extends RuntimeException {

    public InvalidWebhookException(String message) {
        super(message);
    }
}
