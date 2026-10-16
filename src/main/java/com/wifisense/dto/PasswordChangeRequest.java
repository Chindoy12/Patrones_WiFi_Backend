package com.wifisense.dto;

/** Current and new password, each sealed with its own one-time challenge. */
public record PasswordChangeRequest(String currentPassword, String currentChallengeId, String currentEncryptedPassword,
                                    String newPassword, String newChallengeId, String newEncryptedPassword) {
}
