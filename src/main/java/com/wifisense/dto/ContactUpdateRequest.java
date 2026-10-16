package com.wifisense.dto;

/** New recovery email and phone; the current password (sealed) confirms that it is the owner who changes them. */
public record ContactUpdateRequest(String email, String phone, String password, String challengeId,
                                   String encryptedPassword) {
}
