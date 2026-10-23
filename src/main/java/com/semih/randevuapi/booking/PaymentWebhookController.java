package com.semih.randevuapi.booking;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called by the payment provider, not by users, so it is open to everyone (SecurityConfig);
 * the signature check in the gateway is what keeps fake calls out.
 */
@RestController
@Tag(name = "Payments")
public class PaymentWebhookController {

    private final BookingPaymentService paymentService;

    public PaymentWebhookController(BookingPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /** The body is taken as a raw String: the signature is computed over the exact bytes sent. */
    @PostMapping("/api/payments/webhook")
    @Operation(summary = "Payment provider webhook (signed); marks bookings as paid")
    public void webhook(@RequestBody String payload, HttpServletRequest request) {
        paymentService.handleWebhook(payload, request.getHeader(paymentService.signatureHeader()));
    }
}
