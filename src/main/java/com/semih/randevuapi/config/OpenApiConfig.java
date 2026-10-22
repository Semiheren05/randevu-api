package com.semih.randevuapi.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** Swagger UI at /swagger-ui.html. Use "Authorize" with a token from /api/auth/login. */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "randevu-api",
        version = "v1",
        description = "Multi-business online appointment booking API"))
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {
}
