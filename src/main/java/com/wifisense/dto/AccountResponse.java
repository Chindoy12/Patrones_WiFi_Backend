package com.wifisense.dto;

/** The end user's own account. {@code email} is null when the account still has no recovery email. */
public record AccountResponse(String username, String email, String phone) {
}
