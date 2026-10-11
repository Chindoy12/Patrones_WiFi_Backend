package com.wifisense.dto;

/** Rules are checked by PasswordPolicy so every message is consistent and in Spanish. */
public record RegisterRequest(String username, String password) {
}
