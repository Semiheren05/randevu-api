package com.semih.randevuapi.auth.dto;

import jakarta.validation.constraints.Email;
import com.semih.randevuapi.user.Role;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Email @Size(max = 255) String email,
        // BCrypt only uses the first 72 bytes, so longer passwords are rejected instead of silently truncated.
        @NotBlank @Size(min = 8, max = 72) String password,
        @NotBlank @Size(max = 50) String displayName,
        // CUSTOMER books appointments, OWNER manages a business
        @NotNull Role role) {
}
