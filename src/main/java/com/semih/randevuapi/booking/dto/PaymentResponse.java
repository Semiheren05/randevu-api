package com.semih.randevuapi.booking.dto;

import com.semih.randevuapi.booking.PaymentStatus;

/**
 * @param checkoutUrl the provider's payment page; the front end redirects the customer there
 */
public record PaymentResponse(Long bookingId, PaymentStatus status, String checkoutUrl) {
}
