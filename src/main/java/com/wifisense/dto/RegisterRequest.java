package com.wifisense.dto;

/**
 * Rules are checked by PasswordPolicy and ContactPolicy after the sealed password is decrypted, so messages stay in
 * Spanish. {@code email} is required when the username is not an email (it is how the account is recovered);
 * {@code phone} is optional.
 */
public record RegisterRequest(String username, String password, String challengeId, String encryptedPassword,
                              String email, String phone) {

    public RegisterRequest(String username, String password) {
        this(username, password, null, null, null, null);
    }
}
