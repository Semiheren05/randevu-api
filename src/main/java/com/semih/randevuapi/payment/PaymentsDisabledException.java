package com.semih.randevuapi.payment;

/** Online payment is not set up on this server (answered with 503). */
public class PaymentsDisabledException extends RuntimeException {

    public PaymentsDisabledException() {
        super("Online payment is not available; pay at the business");
    }
}
