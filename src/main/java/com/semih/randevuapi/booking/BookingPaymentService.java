package com.semih.randevuapi.booking;

import com.semih.randevuapi.booking.dto.PaymentResponse;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.common.exception.ResourceNotFoundException;
import com.semih.randevuapi.payment.CheckoutRequest;
import com.semih.randevuapi.payment.CheckoutSession;
import com.semih.randevuapi.payment.CompletedPayment;
import com.semih.randevuapi.payment.PaymentGateway;
import com.semih.randevuapi.payment.PaymentProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

/**
 * Online payment of bookings. The flow:
 * <ol>
 *   <li>The customer asks to pay; we open a payment page at the provider and send back its URL.</li>
 *   <li>The customer pays on the provider's page; our server never sees card details.</li>
 *   <li>The provider calls our webhook. Only that signed call marks the booking as paid; the
 *       customer coming back to the success page proves nothing.</li>
 *   <li>If a paid booking is cancelled, the money is refunded.</li>
 * </ol>
 */
@Service
public class BookingPaymentService {

    private static final Logger log = LoggerFactory.getLogger(BookingPaymentService.class);

    private final BookingRepository bookingRepository;
    private final PaymentGateway gateway;
    private final PaymentProperties properties;
    private final Clock clock;

    public BookingPaymentService(BookingRepository bookingRepository,
                                 PaymentGateway gateway,
                                 PaymentProperties properties,
                                 Clock clock) {
        this.bookingRepository = bookingRepository;
        this.gateway = gateway;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PaymentResponse startCheckout(Long bookingId, String customerEmail) {
        Booking booking = bookingRepository.findByIdAndCustomerEmail(bookingId, customerEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found"));
        if (!booking.isConfirmed() || !booking.getStartsAt().isAfter(clock.instant())) {
            throw new ConflictException("Payment not possible", "Only upcoming bookings can be paid");
        }
        if (booking.getPayment().getStatus() == PaymentStatus.PAID) {
            throw new ConflictException("Already paid", "This booking is already paid");
        }
        if (booking.getPrice().signum() == 0) {
            throw new ConflictException("Payment not possible", "This booking is free");
        }
        CheckoutSession session = gateway.createCheckout(new CheckoutRequest(
                "booking-" + booking.getId(),
                booking.getBusiness().getName() + " · " + booking.getOffering().getName(),
                // 250.00 TL -> 25000 kuruş
                booking.getPrice().movePointRight(2).longValueExact(),
                properties.currency(),
                booking.getCustomer().getEmail()));
        booking.getPayment().startCheckout(gateway.name(), session.id());
        return new PaymentResponse(booking.getId(), PaymentStatus.PENDING, session.url());
    }

    /**
     * Handles a provider webhook. Providers retry webhooks, so the same event can arrive more
     * than once; a booking that is already paid is left alone.
     */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        Optional<CompletedPayment> completed = gateway.parseWebhook(payload, signature);
        if (completed.isEmpty()) {
            return;
        }
        CompletedPayment payment = completed.get();
        Optional<Booking> found = bookingRepository.findByPaymentCheckoutId(payment.checkoutId());
        if (found.isEmpty()) {
            log.warn("Payment for unknown checkout {}", payment.checkoutId());
            return;
        }
        Booking booking = found.get();
        if (booking.getPayment().getStatus() != PaymentStatus.PENDING) {
            return;
        }
        booking.getPayment().markPaid(payment.paymentId(), clock.instant());
        // The customer may have cancelled while the payment page was still open
        if (!booking.isConfirmed()) {
            refundIfPaid(booking);
        }
    }

    /**
     * Called when a booking is cancelled. The refund happens inside the cancellation's
     * transaction: if the provider fails, the cancellation fails too, so a cancelled booking
     * is never left with money that was not returned.
     */
    void refundIfPaid(Booking booking) {
        if (booking.getPayment().getStatus() == PaymentStatus.PAID) {
            gateway.refund(booking.getPayment().getReference());
            booking.getPayment().markRefunded(clock.instant());
        }
    }

    /** The header the current provider signs its webhooks with. */
    public String signatureHeader() {
        return gateway.signatureHeader();
    }
}
