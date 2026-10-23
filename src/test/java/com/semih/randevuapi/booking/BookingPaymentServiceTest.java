package com.semih.randevuapi.booking;

import com.semih.randevuapi.business.Business;
import com.semih.randevuapi.business.Offering;
import com.semih.randevuapi.common.exception.ConflictException;
import com.semih.randevuapi.payment.CheckoutRequest;
import com.semih.randevuapi.payment.CheckoutSession;
import com.semih.randevuapi.payment.CompletedPayment;
import com.semih.randevuapi.payment.PaymentGateway;
import com.semih.randevuapi.payment.PaymentProperties;
import com.semih.randevuapi.user.Role;
import com.semih.randevuapi.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingPaymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T07:00:00Z");

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private PaymentGateway gateway;

    private BookingPaymentService paymentService;
    private Booking booking;

    @BeforeEach
    void setUp() {
        PaymentProperties properties = new PaymentProperties(PaymentProperties.Provider.FAKE, "try",
                "https://site.test/ok", "https://site.test/back", null, new PaymentProperties.Fake("secret"));
        paymentService = new BookingPaymentService(bookingRepository, gateway, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));

        User owner = new User("ali@example.com", "hash", "Ali", Role.OWNER);
        User customer = new User("ayse@example.com", "hash", "Ayşe", Role.CUSTOMER);
        Business business = new Business(owner, "kuafor-ali", "Kuaför Ali", null, "İstanbul",
                ZoneId.of("Europe/Istanbul"));
        Offering haircut = new Offering(business, "Saç kesimi", 30, new BigDecimal("250.00"));
        Instant start = NOW.plus(Duration.ofDays(1));
        booking = new Booking(business, haircut, customer, null, new TimeRange(start, start.plusSeconds(1800)), null);
    }

    @Test
    void startingCheckoutOpensAPaymentPageForTheBookingPrice() {
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));
        when(gateway.createCheckout(any())).thenReturn(new CheckoutSession("cs_1", "https://pay.test/cs_1"));
        when(gateway.name()).thenReturn("stripe");

        var response = paymentService.startCheckout(5L, "ayse@example.com");

        ArgumentCaptor<CheckoutRequest> request = ArgumentCaptor.forClass(CheckoutRequest.class);
        verify(gateway).createCheckout(request.capture());
        assertThat(request.getValue().amountMinor()).isEqualTo(25_000);
        assertThat(request.getValue().currency()).isEqualTo("try");
        assertThat(request.getValue().description()).isEqualTo("Kuaför Ali · Saç kesimi");
        assertThat(response.checkoutUrl()).isEqualTo("https://pay.test/cs_1");
        assertThat(booking.getPayment().getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(booking.getPayment().getCheckoutId()).isEqualTo("cs_1");
    }

    @Test
    void aPaidBookingCannotBePaidAgain() {
        booking.getPayment().startCheckout("stripe", "cs_1");
        booking.getPayment().markPaid("pi_1", NOW);
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.startCheckout(5L, "ayse@example.com"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("This booking is already paid");
        verify(gateway, never()).createCheckout(any());
    }

    @Test
    void aCancelledBookingCannotBePaid() {
        booking.cancel(NOW);
        when(bookingRepository.findByIdAndCustomerEmail(5L, "ayse@example.com")).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> paymentService.startCheckout(5L, "ayse@example.com"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void theWebhookMarksThePendingBookingAsPaid() {
        booking.getPayment().startCheckout("stripe", "cs_1");
        when(gateway.parseWebhook("body", "sig")).thenReturn(Optional.of(new CompletedPayment("cs_1", "pi_1")));
        when(bookingRepository.findByPaymentCheckoutId("cs_1")).thenReturn(Optional.of(booking));

        paymentService.handleWebhook("body", "sig");

        assertThat(booking.getPayment().getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(booking.getPayment().getReference()).isEqualTo("pi_1");
        assertThat(booking.getPayment().getPaidAt()).isEqualTo(NOW);
    }

    @Test
    void aRepeatedWebhookChangesNothing() {
        booking.getPayment().startCheckout("stripe", "cs_1");
        booking.getPayment().markPaid("pi_1", NOW.minusSeconds(60));
        when(gateway.parseWebhook("body", "sig")).thenReturn(Optional.of(new CompletedPayment("cs_1", "pi_1")));
        when(bookingRepository.findByPaymentCheckoutId("cs_1")).thenReturn(Optional.of(booking));

        paymentService.handleWebhook("body", "sig");

        assertThat(booking.getPayment().getPaidAt()).isEqualTo(NOW.minusSeconds(60));
    }

    @Test
    void aPaymentForABookingCancelledMeanwhileIsRefundedAtOnce() {
        booking.getPayment().startCheckout("stripe", "cs_1");
        booking.cancel(NOW);
        when(gateway.parseWebhook("body", "sig")).thenReturn(Optional.of(new CompletedPayment("cs_1", "pi_1")));
        when(bookingRepository.findByPaymentCheckoutId("cs_1")).thenReturn(Optional.of(booking));

        paymentService.handleWebhook("body", "sig");

        verify(gateway).refund("pi_1");
        assertThat(booking.getPayment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void onlyPaidBookingsAreRefunded() {
        paymentService.refundIfPaid(booking);
        verify(gateway, never()).refund(any());

        booking.getPayment().startCheckout("stripe", "cs_1");
        booking.getPayment().markPaid("pi_1", NOW);
        paymentService.refundIfPaid(booking);

        verify(gateway).refund("pi_1");
        assertThat(booking.getPayment().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }
}
