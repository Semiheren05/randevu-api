package com.semih.randevuapi.auth.dto;

/**
 * @param expiresIn seconds until the access token expires
 */
public record AuthResponse(String accessToken, String tokenType, long expiresIn) {

    public static AuthResponse bearer(String accessToken, long expiresIn) {
        return new AuthResponse(accessToken, "Bearer", expiresIn);
    }
}
