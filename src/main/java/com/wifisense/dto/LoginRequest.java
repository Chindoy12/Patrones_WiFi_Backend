package com.wifisense.dto;

/**
 * The password arrives sealed (challengeId + encryptedPassword, see PasswordCipher) from the web app; the plain
 * field is only for scripts while plain passwords are allowed. No bean validation: every failure looks the same.
 */
public record LoginRequest(String username, String password, String challengeId, String encryptedPassword) {

    public LoginRequest(String username, String password) {
        this(username, password, null, null);
    }
}
