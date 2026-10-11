package com.wifisense.security;

import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.spec.MGF1ParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Second layer on top of HTTPS: the browser encrypts the password with this server's RSA public key, bound to a
 * one-time challenge. The password never travels (or shows in the browser's network tools) in readable form, and a
 * copied payload cannot be replayed because each challenge works once and expires in two minutes.
 */
@Service
public class PasswordCipher {

    public record Challenge(String challengeId, String publicKey, Instant expiresAt) {
    }

    static final Duration TTL = Duration.ofMinutes(2);
    static final int MAX_PENDING = 10_000;
    private static final OAEPParameterSpec OAEP_SHA256 =
            new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT);

    private final KeyPair keys;
    private final String publicKey;
    private final Map<String, Instant> pending = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final Clock clock;
    private final boolean plainAllowed;

    public PasswordCipher(Clock clock, SecurityProperties properties) {
        this.clock = clock;
        this.plainAllowed = properties.plainPasswordsAllowed();
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            this.keys = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is not available", e);
        }
        this.publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
    }

    public Challenge issue() {
        Instant now = Instant.now(clock);
        pending.values().removeIf(expiry -> expiry.isBefore(now));
        if (pending.size() >= MAX_PENDING) {
            throw new LoginBlockedException();
        }
        byte[] id = new byte[24];
        random.nextBytes(id);
        String challengeId = Base64.getUrlEncoder().withoutPadding().encodeToString(id);
        Instant expiresAt = now.plus(TTL);
        pending.put(challengeId, expiresAt);
        return new Challenge(challengeId, publicKey, expiresAt);
    }

    /**
     * The password from a request: decrypted when it comes sealed, or taken as is when plain passwords are still
     * accepted (scripts and older clients). Throws {@link InvalidEncryptedPasswordException} otherwise.
     */
    public String reveal(String plainPassword, String challengeId, String encryptedPassword) {
        if (encryptedPassword != null) {
            return decrypt(challengeId, encryptedPassword);
        }
        if (plainAllowed && plainPassword != null) {
            return plainPassword;
        }
        throw new InvalidEncryptedPasswordException();
    }

    String decrypt(String challengeId, String encryptedPassword) {
        Instant expiresAt = challengeId == null ? null : pending.remove(challengeId);
        if (expiresAt == null || expiresAt.isBefore(Instant.now(clock))) {
            throw new InvalidEncryptedPasswordException();
        }
        String plain;
        try {
            Cipher cipher = Cipher.getInstance("RSA/ECB/OAEPPadding");
            cipher.init(Cipher.DECRYPT_MODE, keys.getPrivate(), OAEP_SHA256);
            plain = new String(cipher.doFinal(Base64.getDecoder().decode(encryptedPassword)), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new InvalidEncryptedPasswordException();
        }
        String prefix = challengeId + ":";
        if (!plain.startsWith(prefix)) {
            throw new InvalidEncryptedPasswordException();
        }
        return plain.substring(prefix.length());
    }
}
