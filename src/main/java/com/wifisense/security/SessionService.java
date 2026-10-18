package com.wifisense.security;

import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.UserResponse;
import com.wifisense.model.RefreshToken;
import com.wifisense.model.User;
import com.wifisense.repository.RefreshTokenRepository;
import com.wifisense.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Sessions: a short access token (JWT) plus a refresh token that renews it without asking for the password again.
 * <ul>
 *   <li>The refresh token carries 256 random bits; only their SHA-256 is stored.</li>
 *   <li>Rotation: each refresh token works once and is replaced by the next one of the same family (one per login).
 *       A token presented again after being used means someone copied it, so the whole family is revoked.</li>
 *   <li>Changing or resetting the password closes every session of the account; signing out closes this one.</li>
 * </ul>
 */
@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private static final Pattern TOKEN = Pattern.compile("^[A-Za-z0-9_-]{43}$");

    private final UserRepository users;
    private final RefreshTokenRepository tokens;
    private final TokenService accessTokens;
    private final SessionProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public SessionService(UserRepository users, RefreshTokenRepository tokens, TokenService accessTokens,
                          SessionProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.accessTokens = accessTokens;
        this.properties = properties;
        this.clock = clock;
    }

    /** Opens a new session for an account that just proved its password ({@code username} as stored). */
    @Transactional
    public AuthResponse start(String username) {
        User user = users.findByUsername(username).orElseThrow(InvalidSessionException::new);
        return start(user);
    }

    @Transactional
    public AuthResponse start(User user) {
        return issue(user, UUID.randomUUID(), Instant.now(clock));
    }

    /**
     * Exchanges a refresh token for a new pair. Revocations made here are kept even though the call fails, so a
     * stolen token stays useless.
     */
    @Transactional(noRollbackFor = InvalidSessionException.class)
    public AuthResponse refresh(String refreshToken) {
        String hash = hashOf(refreshToken);
        Instant now = Instant.now(clock);
        boolean used = tokens.use(hash, now) == 1;
        RefreshToken token = tokens.findByTokenHash(hash).orElseThrow(InvalidSessionException::new);
        if (!used) {
            if (isReuse(token, now)) {
                tokens.revokeFamily(token.getFamilyId(), now);
                log.warn("A used refresh token was presented again: every session of that login was closed (user {})",
                        token.getUser().getId());
            }
            throw new InvalidSessionException();
        }
        User user = token.getUser();
        if (!user.isEnabled() || startedBeforePasswordChange(token, user)) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new InvalidSessionException();
        }
        return issue(user, token.getFamilyId(), now);
    }

    /** Signing out: the session of that refresh token stops working. Unknown tokens are ignored. */
    @Transactional
    public void end(String refreshToken) {
        if (refreshToken == null || !TOKEN.matcher(refreshToken).matches()) {
            return;
        }
        tokens.findByTokenHash(hash(refreshToken))
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), Instant.now(clock)));
    }

    /** Closes every session of the account (password changed or reset). */
    @Transactional
    public int endAll(Long userId) {
        return tokens.revokeAllOf(userId, Instant.now(clock));
    }

    /** Nightly cleanup: expired tokens can no longer renew anything, nor reveal a reuse. */
    @Transactional
    public int purgeExpired() {
        return tokens.deleteExpiredBefore(Instant.now(clock));
    }

    private AuthResponse issue(User user, UUID family, Instant now) {
        byte[] secret = new byte[32];
        random.nextBytes(secret);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        Instant refreshExpiresAt = now.plus(properties.refreshTtl());
        tokens.save(new RefreshToken(user, family, hash(refreshToken), now, refreshExpiresAt));
        TokenService.IssuedToken access = accessTokens.issue(new UsernamePasswordAuthenticationToken(
                user.getUsername(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
        return new AuthResponse(access.value(), access.expiresAt(), UserResponse.from(user), refreshToken,
                refreshExpiresAt);
    }

    /** Used and not revoked, longer ago than the grace period: a copy, not the same browser retrying. */
    private boolean isReuse(RefreshToken token, Instant now) {
        return token.getUsedAt() != null && token.getRevokedAt() == null
                && token.getUsedAt().isBefore(now.minus(properties.reuseGrace()));
    }

    private static boolean startedBeforePasswordChange(RefreshToken token, User user) {
        return user.getPasswordChangedAt() != null && token.getCreatedAt().isBefore(user.getPasswordChangedAt());
    }

    private static String hashOf(String refreshToken) {
        if (refreshToken == null || !TOKEN.matcher(refreshToken).matches()) {
            throw new InvalidSessionException();
        }
        return hash(refreshToken);
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
