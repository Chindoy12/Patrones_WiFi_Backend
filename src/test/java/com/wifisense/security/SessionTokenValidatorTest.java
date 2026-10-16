package com.wifisense.security;

import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SessionTokenValidatorTest {

    private final UserRepository users = mock(UserRepository.class);
    private final SessionTokenValidator validator = new SessionTokenValidator(users);

    private static Jwt token(String subject, Instant issuedAt) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject).issuedAt(issuedAt)
                .expiresAt(issuedAt.plusSeconds(3600)).build();
    }

    @Test
    void aSessionOpenedBeforeThePasswordChangeStopsWorking() {
        User ana = new User("ana", "ana@gmail.com", "h", User.Role.USER);
        ana.changePassword("h2", Instant.parse("2026-10-16T15:00:00.700Z"));
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));

        assertThat(validator.validate(token("ana", Instant.parse("2026-10-16T14:00:00Z"))).hasErrors()).isTrue();
        // Issued in the same second as the change (JWT times have no milliseconds): the new session is valid.
        assertThat(validator.validate(token("ana", Instant.parse("2026-10-16T15:00:00Z"))).hasErrors()).isFalse();
    }

    @Test
    void deletedOrDisabledAccountsLoseTheirSessions() {
        User off = new User("off", "off@gmail.com", "h", User.Role.USER);
        ReflectionTestUtils.setField(off, "enabled", false);
        when(users.findByUsername("off")).thenReturn(Optional.of(off));

        assertThat(validator.validate(token("gone", Instant.now())).hasErrors()).isTrue();
        assertThat(validator.validate(token("off", Instant.now())).hasErrors()).isTrue();
    }

    @Test
    void accountsThatNeverChangedTheirPasswordKeepWorking() {
        when(users.findByUsername("ana")).thenReturn(Optional.of(new User("ana", "a@b.co", "h", User.Role.USER)));

        assertThat(validator.validate(token("ana", Instant.now())).hasErrors()).isFalse();
    }
}
