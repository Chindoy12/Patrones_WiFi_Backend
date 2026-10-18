package com.wifisense.service;

import com.wifisense.mail.MailDispatcher;
import com.wifisense.mail.MailProperties;
import com.wifisense.mail.RecoveryEmail;
import com.wifisense.model.PasswordResetToken;
import com.wifisense.model.User;
import com.wifisense.repository.PasswordResetTokenRepository;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.RecoveryThrottle;
import com.wifisense.security.SessionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * "Forgot your password": a one-time link by email to write a new one.
 * <ul>
 *   <li>The answer to a request is always the same, whether or not the email has an account, and the email goes out
 *       in the background, so the endpoint cannot be used to find out who is registered.</li>
 *   <li>The link carries 256 random bits; only their SHA-256 is stored. It works once and expires (30 min).</li>
 *   <li>Resetting the password closes every open session of the account: the access tokens through
 *       users.password_changed_at and the refresh tokens by revoking them.</li>
 * </ul>
 */
@Service
public class PasswordResetService {

    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ContactPolicy contacts;
    private final PasswordPolicy policy;
    private final PasswordEncoder encoder;
    private final PasswordCipher cipher;
    private final RecoveryThrottle throttle;
    private final MailDispatcher mail;
    private final MailProperties properties;
    private final SessionService sessions;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, ContactPolicy contacts,
                                PasswordPolicy policy, PasswordEncoder encoder, PasswordCipher cipher,
                                RecoveryThrottle throttle, MailDispatcher mail, MailProperties properties,
                                SessionService sessions, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.contacts = contacts;
        this.policy = policy;
        this.encoder = encoder;
        this.cipher = cipher;
        this.throttle = throttle;
        this.mail = mail;
        this.properties = properties;
        this.sessions = sessions;
        this.clock = clock;
    }

    /** Sends a recovery link if the email belongs to an active account. Never says whether it does. */
    @Transactional
    public void requestLink(String rawEmail, String clientAddress) {
        throttle.acquireAddress(clientAddress);
        Optional<String> email = contacts.email(rawEmail);
        if (email.isEmpty()) {
            throw new IllegalArgumentException(ContactPolicy.EMAIL_ERROR);
        }
        Optional<User> account = users.findByEmailIgnoreCase(email.get())
                .filter(User::isEnabled).filter(User::hasRealEmail);
        if (account.isEmpty() || !throttle.acquireEmail(email.get())) {
            return;
        }
        User user = account.get();
        Instant now = Instant.now(clock);
        tokens.deletePendingOf(user.getId());
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        tokens.save(new PasswordResetToken(user, hash(token), now, now.plus(properties.resetTtl())));
        // The token travels in the fragment (#), which browsers never send to servers or put in Referer headers.
        String link = properties.frontendUrl().replaceAll("/+$", "") + "/restablecer#t=" + token;
        mail.sendLater(RecoveryEmail.build(user.getEmail(), user.getUsername(), link, properties.resetTtl()));
    }

    /** Sets the new password if the link is valid; closes every session that was open. */
    @Transactional
    public void reset(String token, String plainPassword, String challengeId, String encryptedPassword) {
        String password = cipher.reveal(plainPassword, challengeId, encryptedPassword);
        if (token == null || !TOKEN.matcher(token).matches()) {
            throw new InvalidResetLinkException();
        }
        Instant now = Instant.now(clock);
        PasswordResetToken link = tokens.findByTokenHash(hash(token))
                .filter(t -> t.isUsable(now)).orElseThrow(InvalidResetLinkException::new);
        List<String> errors = policy.passwordViolations(password);
        if (!errors.isEmpty()) {
            throw new InvalidAccountException(errors);
        }
        User user = link.getUser();
        user.changePassword(encoder.encode(password), now);
        sessions.endAll(user.getId());
        link.use(now);
        tokens.deletePendingOf(user.getId());
    }

    /** Hourly cleanup of expired links. */
    @Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
    @Transactional
    public void purgeExpired() {
        tokens.deleteExpiredBefore(Instant.now(clock));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }
}
