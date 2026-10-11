package com.wifisense.security;

import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordCipherTest {

    private static final class MovableClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T12:00:00Z");

        void advance(Duration duration) { now = now.plus(duration); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private static SecurityProperties properties(boolean plainAllowed) {
        return new SecurityProperties("test-secret-that-is-at-least-32-bytes-long", Duration.ofHours(8),
                List.of("http://localhost:5173"), plainAllowed);
    }

    private final MovableClock clock = new MovableClock();
    private final PasswordCipher cipher = new PasswordCipher(clock, properties(false));

    /** Does what the browser does with Web Crypto: RSA-OAEP with SHA-256 over "challengeId:password". */
    private static String seal(PasswordCipher.Challenge challenge, String text) throws Exception {
        PublicKey key = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(challenge.publicKey())));
        Cipher rsa = Cipher.getInstance("RSA/ECB/OAEPPadding");
        rsa.init(Cipher.ENCRYPT_MODE, key,
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
        return Base64.getEncoder().encodeToString(rsa.doFinal(text.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void sealedPasswordIsRecovered() throws Exception {
        var challenge = cipher.issue();
        String sealed = seal(challenge, challenge.challengeId() + ":Redes#2026ñ");

        assertThat(cipher.reveal(null, challenge.challengeId(), sealed)).isEqualTo("Redes#2026ñ");
    }

    @Test
    void aCopiedPayloadCannotBeReplayed() throws Exception {
        var challenge = cipher.issue();
        String sealed = seal(challenge, challenge.challengeId() + ":Redes#2026a");
        cipher.reveal(null, challenge.challengeId(), sealed);

        assertThatThrownBy(() -> cipher.reveal(null, challenge.challengeId(), sealed))
                .isInstanceOf(InvalidEncryptedPasswordException.class);
    }

    @Test
    void challengesExpire() throws Exception {
        var challenge = cipher.issue();
        String sealed = seal(challenge, challenge.challengeId() + ":Redes#2026a");
        clock.advance(PasswordCipher.TTL.plusSeconds(1));

        assertThatThrownBy(() -> cipher.reveal(null, challenge.challengeId(), sealed))
                .isInstanceOf(InvalidEncryptedPasswordException.class);
    }

    @Test
    void payloadMustBeBoundToItsChallenge() throws Exception {
        var first = cipher.issue();
        var second = cipher.issue();
        String sealedForFirst = seal(first, first.challengeId() + ":Redes#2026a");

        assertThatThrownBy(() -> cipher.reveal(null, second.challengeId(), sealedForFirst))
                .isInstanceOf(InvalidEncryptedPasswordException.class);
        assertThatThrownBy(() -> cipher.reveal(null, first.challengeId(), "not-base64!"))
                .isInstanceOf(InvalidEncryptedPasswordException.class);
    }

    @Test
    void plainPasswordsOnlyWhenAllowed() {
        assertThatThrownBy(() -> cipher.reveal("Redes#2026a", null, null))
                .isInstanceOf(InvalidEncryptedPasswordException.class);
        assertThat(new PasswordCipher(clock, properties(true)).reveal("Redes#2026a", null, null)).isEqualTo("Redes#2026a");
    }
}
