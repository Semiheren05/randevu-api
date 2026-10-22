package com.semih.randevuapi.security;

import com.semih.randevuapi.user.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies HMAC-signed access tokens.
 * The token subject is the user's email; the role travels as a claim so
 * requests can be authorized without a database lookup.
 */
@Service
public class JwtService {

    private static final String ROLE_CLAIM = "role";

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.secret()));
        this.expiration = properties.expiration();
        this.clock = clock;
    }

    public String generateToken(String email, Role role) {
        Instant now = clock.instant();
        return Jwts.builder()
                .subject(email)
                .claim(ROLE_CLAIM, role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /** Returns the claims of a valid token, or empty if it is malformed, tampered with or expired. */
    public Optional<TokenClaims> parse(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .clock(() -> Date.from(clock.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            String role = claims.get(ROLE_CLAIM, String.class);
            if (claims.getSubject() == null || role == null) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(claims.getSubject(), Role.valueOf(role)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getExpiration() {
        return expiration;
    }
}
