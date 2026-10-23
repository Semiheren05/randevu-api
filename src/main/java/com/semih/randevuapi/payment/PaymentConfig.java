package com.semih.randevuapi.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/** Picks the payment provider from app.payments.provider (PAYMENT_PROVIDER). */
@Configuration
public class PaymentConfig {

    @Bean
    PaymentGateway paymentGateway(PaymentProperties properties,
                                  RestClient.Builder restClientBuilder,
                                  ObjectMapper objectMapper,
                                  Clock clock) {
        return switch (properties.provider()) {
            case NONE -> new DisabledPaymentGateway();
            case STRIPE -> new StripePaymentGateway(properties, restClientBuilder, objectMapper, clock);
            case FAKE -> new FakePaymentGateway(properties, objectMapper);
        };
    }
}
