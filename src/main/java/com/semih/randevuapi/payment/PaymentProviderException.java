package com.semih.randevuapi.payment;

/** The payment provider could not be reached or refused the request (answered with 502). */
public class PaymentProviderException extends RuntimeException {

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
