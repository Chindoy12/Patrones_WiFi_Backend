package com.wifisense.service;

import com.wifisense.dto.LoginRequest;
import com.wifisense.dto.RegisterRequest;
import com.wifisense.security.LoginAttemptService;
import com.wifisense.security.LoginBlockedException;
import com.wifisense.security.InvalidEncryptedPasswordException;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.RegistrationBlockedException;
import com.wifisense.security.RegistrationThrottle;
import com.wifisense.security.TokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final AuthenticationManager manager = mock(AuthenticationManager.class);
    private final LoginAttemptService attempts = mock(LoginAttemptService.class);
    private final UserService users = mock(UserService.class);
    private final RegistrationThrottle registrations = mock(RegistrationThrottle.class);
    private final PasswordCipher cipher = mock(PasswordCipher.class);
    private final AuthService service = new AuthService(manager, mock(TokenService.class), users,
            attempts, new PasswordPolicy(), registrations, cipher);

    {
        when(cipher.reveal(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void oversizedInputFailsWithoutReachingTheDatabase() {
        assertThatThrownBy(() -> service.login(new LoginRequest("a".repeat(10_000), "x"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);

        verify(manager, never()).authenticate(any());
        verify(attempts).recordFailure(any(), eq("1.2.3.4"));
    }

    @Test
    void wrongPasswordIsRecordedAsFailure() {
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest("analyst", "Redes#2026a"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);
        verify(attempts).recordFailure("analyst", "1.2.3.4");
    }

    @Test
    void blockedClientIsRejectedBeforeCheckingCredentials() {
        when(attempts.isBlocked("analyst", "1.2.3.4")).thenReturn(true);

        assertThatThrownBy(() -> service.login(new LoginRequest("analyst", "Redes#2026a"), "1.2.3.4"))
                .isInstanceOf(LoginBlockedException.class);
        verify(manager, never()).authenticate(any());
    }

    @Test
    void throttledAddressCannotRegister() {
        doThrow(new RegistrationBlockedException()).when(registrations).check("1.2.3.4");

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana", "Redes#2026a"), "1.2.3.4"))
                .isInstanceOf(RegistrationBlockedException.class);
        verify(users, never()).register(any(), any());
    }

    @Test
    void failedRegistrationIsNotCounted() {
        when(users.register("ana", "weak")).thenThrow(new InvalidAccountException(java.util.List.of("x")));

        assertThatThrownBy(() -> service.register(new RegisterRequest("ana", "weak"), "1.2.3.4"))
                .isInstanceOf(InvalidAccountException.class);
        verify(registrations, never()).record(any());
    }

    @Test
    void anExpiredOrForgedSealedPasswordIsJustWrongCredentials() {
        when(cipher.reveal(null, "old", "sealed")).thenThrow(new InvalidEncryptedPasswordException());

        assertThatThrownBy(() -> service.login(new LoginRequest("analyst", null, "old", "sealed"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);
        verify(attempts).recordFailure("analyst", "1.2.3.4");
        verify(manager, never()).authenticate(any());
    }

    @Test
    void sealedPasswordIsDecryptedBeforeAuthenticating() {
        when(cipher.reveal(null, "c1", "sealed")).thenReturn("Redes#2026a");
        when(manager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));

        assertThatThrownBy(() -> service.login(new LoginRequest("analyst", null, "c1", "sealed"), "1.2.3.4"))
                .isInstanceOf(BadCredentialsException.class);
        verify(manager).authenticate(argThat(token -> "Redes#2026a".equals(token.getCredentials())));
    }
}
