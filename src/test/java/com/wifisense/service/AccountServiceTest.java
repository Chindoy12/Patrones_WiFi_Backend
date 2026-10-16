package com.wifisense.service;

import com.wifisense.dto.AccountResponse;
import com.wifisense.dto.AuthResponse;
import com.wifisense.dto.ContactUpdateRequest;
import com.wifisense.dto.PasswordChangeRequest;
import com.wifisense.model.User;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.LoginAttemptService;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordCipher cipher = mock(PasswordCipher.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final TokenService tokens = mock(TokenService.class);
    private final AccountService service = new AccountService(users, new ContactPolicy(), new PasswordPolicy(), encoder,
            cipher, attempts, tokens, Clock.fixed(NOW, ZoneOffset.UTC));
    private final User ana = new User("ana", "ana@wifisense.local", "hash", User.Role.USER);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ana, "id", 4L);
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(cipher.reveal(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(encoder.matches("Actual#2026a", "hash")).thenReturn(true);
        when(encoder.encode(any())).thenReturn("new-hash");
        when(tokens.issue(any())).thenReturn(new TokenService.IssuedToken("jwt", NOW.plusSeconds(3600)));
    }

    @Test
    void aPlaceholderEmailIsShownAsMissing() {
        assertThat(service.get("ana")).isEqualTo(new AccountResponse("ana", null, null));
    }

    @Test
    void contactChangesNeedTheCurrentPassword() {
        AccountResponse updated = service.updateContact("ana",
                new ContactUpdateRequest("Ana@Gmail.com", "300 123 4567", "Actual#2026a", null, null), "1.2.3.4");

        assertThat(updated).isEqualTo(new AccountResponse("ana", "ana@gmail.com", "+573001234567"));
        assertThatThrownBy(() -> service.updateContact("ana",
                new ContactUpdateRequest("x@gmail.com", null, "Mala#2026ab", null, null), "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("La contraseña actual no es correcta.");
        verify(attempts).recordFailure("ana", "1.2.3.4");
    }

    @Test
    void anEmailOfAnotherAccountIsRefused() {
        User other = new User("eva", "eva@gmail.com", "h", User.Role.USER);
        ReflectionTestUtils.setField(other, "id", 5L);
        when(users.findByEmailIgnoreCase("eva@gmail.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.updateContact("ana",
                new ContactUpdateRequest("eva@gmail.com", null, "Actual#2026a", null, null), "1.2.3.4"))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void changingThePasswordClosesOtherSessionsAndOpensANewOne() {
        AuthResponse session = service.changePassword("ana",
                new PasswordChangeRequest("Actual#2026a", null, null, "Nueva#2026ab", null, null), "1.2.3.4");

        assertThat(ana.getPasswordHash()).isEqualTo("new-hash");
        assertThat(ana.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(session.token()).isEqualTo("jwt");
    }

    @Test
    void theNewPasswordMustFollowThePolicyAndBeDifferent() {
        assertThatThrownBy(() -> service.changePassword("ana",
                new PasswordChangeRequest("Actual#2026a", null, null, "Actual#2026a", null, null), "1.2.3.4"))
                .isInstanceOfSatisfying(InvalidAccountException.class, e -> assertThat(e.getErrors())
                        .containsExactly("La contraseña nueva debe ser distinta de la actual."));
        assertThatThrownBy(() -> service.changePassword("ana",
                new PasswordChangeRequest("Actual#2026a", null, null, "debil", null, null), "1.2.3.4"))
                .isInstanceOf(InvalidAccountException.class);
        assertThat(ana.getPasswordHash()).isEqualTo("hash");
    }

    @Test
    void aLockedAccountCannotTryPasswords() {
        when(attempts.isBlocked("ana", "6.6.6.6")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword("ana",
                new PasswordChangeRequest("Actual#2026a", null, null, "Nueva#2026ab", null, null), "6.6.6.6"))
                .isInstanceOf(LoginBlockedException.class);
    }
}
