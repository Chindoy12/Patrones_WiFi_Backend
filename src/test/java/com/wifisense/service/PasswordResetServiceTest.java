package com.wifisense.service;

import com.wifisense.mail.EmailMessage;
import com.wifisense.mail.MailDispatcher;
import com.wifisense.mail.MailProperties;
import com.wifisense.model.PasswordResetToken;
import com.wifisense.model.User;
import com.wifisense.repository.PasswordResetTokenRepository;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.ContactPolicy;
import com.wifisense.security.PasswordCipher;
import com.wifisense.security.PasswordPolicy;
import com.wifisense.security.RecoveryBlockedException;
import com.wifisense.security.RecoveryThrottle;
import com.wifisense.security.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final UserRepository users = mock(UserRepository.class);
    private final PasswordResetTokenRepository tokens = mock(PasswordResetTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final PasswordCipher cipher = mock(PasswordCipher.class);
    private final RecoveryThrottle throttle = mock(RecoveryThrottle.class);
    private final MailDispatcher mail = mock(MailDispatcher.class);
    private final SessionService sessions = mock(SessionService.class);
    private final PasswordResetService service = new PasswordResetService(users, tokens, new ContactPolicy(),
            new PasswordPolicy(), encoder, cipher, throttle, mail,
            new MailProperties("key", "wifisense@gmail.com", "WiFiSense", "https://wifisense.example.app/",
                    Duration.ofMinutes(30), false),
            sessions, Clock.fixed(NOW, ZoneOffset.UTC));
    private final User ana = new User("ana", "ana@gmail.com", "old-hash", User.Role.USER);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ana, "id", 4L);
        when(throttle.acquireEmail(any())).thenReturn(true);
        when(cipher.reveal(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(encoder.encode(any())).thenReturn("new-hash");
    }

    private String sentToken() {
        ArgumentCaptor<EmailMessage> email = ArgumentCaptor.forClass(EmailMessage.class);
        verify(mail).sendLater(email.capture());
        Matcher link = Pattern.compile("https://wifisense\\.example\\.app/restablecer#t=([A-Za-z0-9_-]{43})")
                .matcher(email.getValue().text());
        assertThat(link.find()).isTrue();
        assertThat(email.getValue().to()).isEqualTo("ana@gmail.com");
        return link.group(1);
    }

    @Test
    void sendsAOneTimeLinkAndStoresOnlyItsHash() {
        when(users.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(ana));

        service.requestLink("  ANA@gmail.com ", "1.2.3.4");

        String token = sentToken();
        ArgumentCaptor<PasswordResetToken> stored = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(stored.capture());
        assertThat(stored.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(token)).doesNotContain(token);
        assertThat(stored.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(30)));
        verify(tokens).deletePendingOf(4L);
    }

    @Test
    void unknownEmailsAndPlaceholderAccountsGetTheSameSilence() {
        when(users.findByEmailIgnoreCase("pepe@wifisense.local"))
                .thenReturn(Optional.of(new User("pepe", "pepe@wifisense.local", "h", User.Role.USER)));

        service.requestLink("nadie@gmail.com", "1.2.3.4");
        service.requestLink("pepe@wifisense.local", "1.2.3.4");

        verifyNoInteractions(mail);
        verify(tokens, never()).save(any());
    }

    @Test
    void aFloodedEmailGetsNoMoreLinksAndAFloodingAddressIsStopped() {
        when(users.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(ana));
        when(throttle.acquireEmail("ana@gmail.com")).thenReturn(false);
        service.requestLink("ana@gmail.com", "1.2.3.4");
        verifyNoInteractions(mail);

        doThrow(new RecoveryBlockedException()).when(throttle).acquireAddress("6.6.6.6");
        assertThatThrownBy(() -> service.requestLink("ana@gmail.com", "6.6.6.6"))
                .isInstanceOf(RecoveryBlockedException.class);
    }

    @Test
    void aValidLinkChangesThePasswordOnceAndClosesTheSessions() {
        when(users.findByEmailIgnoreCase("ana@gmail.com")).thenReturn(Optional.of(ana));
        service.requestLink("ana@gmail.com", "1.2.3.4");
        String token = sentToken();
        PasswordResetToken stored = new PasswordResetToken(ana, PasswordResetService.hash(token), NOW,
                NOW.plusSeconds(1800));
        when(tokens.findByTokenHash(PasswordResetService.hash(token))).thenReturn(Optional.of(stored));

        service.reset(token, "Nueva#2026a", null, null);

        assertThat(ana.getPasswordHash()).isEqualTo("new-hash");
        assertThat(ana.getPasswordChangedAt()).isEqualTo(NOW);
        assertThat(stored.getUsedAt()).isEqualTo(NOW);
        verify(sessions).endAll(ana.getId());
        assertThatThrownBy(() -> service.reset(token, "Otra#2026ab", null, null))
                .isInstanceOf(InvalidResetLinkException.class);
    }

    @Test
    void expiredMalformedOrUnknownLinksFailTheSameWay() {
        PasswordResetToken expired = new PasswordResetToken(ana, PasswordResetService.hash("e".repeat(43)),
                NOW.minusSeconds(3600), NOW.minusSeconds(1));
        when(tokens.findByTokenHash(PasswordResetService.hash("e".repeat(43)))).thenReturn(Optional.of(expired));

        for (String token : new String[]{"e".repeat(43), "corto", null, "u".repeat(43)}) {
            assertThatThrownBy(() -> service.reset(token, "Nueva#2026a", null, null))
                    .isInstanceOf(InvalidResetLinkException.class);
        }
        assertThat(ana.getPasswordHash()).isEqualTo("old-hash");
    }

    @Test
    void theNewPasswordFollowsThePolicy() {
        PasswordResetToken valid = new PasswordResetToken(ana, PasswordResetService.hash("v".repeat(43)), NOW,
                NOW.plusSeconds(60));
        when(tokens.findByTokenHash(PasswordResetService.hash("v".repeat(43)))).thenReturn(Optional.of(valid));

        assertThatThrownBy(() -> service.reset("v".repeat(43), "debil", null, null))
                .isInstanceOf(InvalidAccountException.class);
        assertThat(valid.getUsedAt()).isNull();
    }

    @Test
    void anInvalidEmailIsAnsweredInSpanish() {
        assertThatThrownBy(() -> service.requestLink("no es un correo", "1.2.3.4"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage(ContactPolicy.EMAIL_ERROR);
    }
}
