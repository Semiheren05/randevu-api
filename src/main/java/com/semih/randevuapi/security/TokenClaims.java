package com.semih.randevuapi.security;

import com.semih.randevuapi.user.Role;

/** The identity carried inside a verified access token. */
public record TokenClaims(String email, Role role) {
}
