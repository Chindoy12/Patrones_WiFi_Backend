package com.wifisense.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The one way every secret that is only checked, never read back, is stored: the SHA-256 of the session refresh
 * tokens, the password reset links, the agent keys and the installation codes, as 64 hexadecimal characters. A copy
 * of the database is not enough to use any of them.
 */
public final class TokenHash {

    private TokenHash() {
    }

    public static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
