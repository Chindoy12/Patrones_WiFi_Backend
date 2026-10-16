package com.wifisense.dto;

/** The token from the email link and the new password, sealed like at login. */
public record ResetPasswordRequest(String token, String password, String challengeId, String encryptedPassword) {
}
