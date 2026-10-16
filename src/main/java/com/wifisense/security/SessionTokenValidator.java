package com.wifisense.security;

import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * Rejects sessions that should no longer work even though their signature is valid: the account was deleted or
 * disabled, or its password changed after the session started (a reset closes every open session).
 */
@Component
public class SessionTokenValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error REVOKED = new OAuth2Error("invalid_token", "La sesión ya no es válida.", null);

    private final UserRepository users;

    public SessionTokenValidator(UserRepository users) {
        this.users = users;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt token) {
        Optional<User> user = users.findByUsername(token.getSubject());
        if (user.isEmpty() || !user.get().isEnabled() || startedBeforePasswordChange(token, user.get())) {
            return OAuth2TokenValidatorResult.failure(REVOKED);
        }
        return OAuth2TokenValidatorResult.success();
    }

    /** JWT times have one-second precision, so the change is compared at that precision too. */
    static boolean startedBeforePasswordChange(Jwt token, User user) {
        Instant changed = user.getPasswordChangedAt();
        Instant issued = token.getIssuedAt();
        return changed != null && (issued == null || issued.isBefore(changed.truncatedTo(ChronoUnit.SECONDS)));
    }
}
