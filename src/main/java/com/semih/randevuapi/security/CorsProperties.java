package com.semih.randevuapi.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/** Origins (e.g. the website's own domain) allowed to call the API from a browser. */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
