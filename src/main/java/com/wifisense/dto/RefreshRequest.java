package com.wifisense.dto;

/** No bean validation: a missing or malformed token is just an ended session (401). */
public record RefreshRequest(String refreshToken) {
}
