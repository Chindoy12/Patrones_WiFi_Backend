package com.wifisense.dto;

import java.time.Instant;

/**
 * A session: {@code token} is the access token for the Authorization header; {@code refreshToken} renews it
 * (POST /api/auth/refresh) and works only once.
 */
public record AuthResponse(String token, Instant expiresAt, UserResponse user, String refreshToken,
                           Instant refreshExpiresAt) {

    public AuthResponse(String token, Instant expiresAt, UserResponse user) {
        this(token, expiresAt, user, null, null);
    }
}
