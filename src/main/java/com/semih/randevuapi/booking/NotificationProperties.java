package com.semih.randevuapi.booking;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param mailFrom sender address for booking emails; empty uses the mail server's default
 */
@ConfigurationProperties(prefix = "app.notifications")
public record NotificationProperties(String mailFrom) {
}
