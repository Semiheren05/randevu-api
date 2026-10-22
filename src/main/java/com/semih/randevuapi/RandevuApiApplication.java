package com.semih.randevuapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point. The default in-memory user Spring Boot would create is disabled:
 * users live in PostgreSQL and authenticate with JWTs.
 */
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RandevuApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(RandevuApiApplication.class, args);
    }
}
