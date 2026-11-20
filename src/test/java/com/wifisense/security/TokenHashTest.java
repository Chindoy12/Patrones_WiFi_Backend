package com.wifisense.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHashTest {

    @Test
    void isTheSha256InHexadecimalAsTheDatabaseChecksIt() {
        // the known SHA-256 of "abc" (FIPS 180-2); the tables only accept 64 lowercase hexadecimal characters
        assertThat(TokenHash.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .matches("^[0-9a-f]{64}$");
    }

    @Test
    void theSameSecretAlwaysGivesTheSameHashAndAnotherOneADifferentHash() {
        assertThat(TokenHash.sha256("wsa_secreto")).isEqualTo(TokenHash.sha256("wsa_secreto"))
                .isNotEqualTo(TokenHash.sha256("wsa_secreta"));
    }
}
