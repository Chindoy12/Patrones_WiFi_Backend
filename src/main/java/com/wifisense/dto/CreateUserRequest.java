package com.wifisense.dto;

import com.wifisense.model.User;
import jakarta.validation.constraints.NotNull;

/** Username and password rules are checked by PasswordPolicy so every message is consistent and in Spanish. */
public record CreateUserRequest(String username, String password, @NotNull User.Role role,
                                String challengeId, String encryptedPassword) {

    public CreateUserRequest(String username, String password, User.Role role) {
        this(username, password, role, null, null);
    }

    public CreateUserRequest withPassword(String plainPassword) {
        return new CreateUserRequest(username, plainPassword, role);
    }
}
