package com.semih.randevuapi.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * @param secret     base64-encoded HMAC key, at least 32 bytes after decoding
 * @param expiration how long an access token stays valid
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotNull Duration expiration) {
}
