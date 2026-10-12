package com.wifisense.dto;

/** Rules are checked by PasswordPolicy after the sealed password is decrypted, so messages stay in Spanish. */
public record RegisterRequest(String username, String password, String challengeId, String encryptedPassword) {

    public RegisterRequest(String username, String password) {
        this(username, password, null, null);
    }
}
