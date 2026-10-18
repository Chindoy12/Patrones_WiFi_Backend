package com.wifisense.security;

import com.wifisense.dto.AuthResponse;
import com.wifisense.model.RefreshToken;
import com.wifisense.model.User;
import com.wifisense.repository.RefreshTokenRepository;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-17T15:00:00Z");
    private static final String TOKEN = "a".repeat(43);

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final TokenService accessTokens = mock(TokenService.class);
    private final SessionService sessions = new SessionService(users, tokens, accessTokens,
            new SessionProperties(Duration.ofDays(14), Duration.ofSeconds(10)), Clock.fixed(NOW, ZoneOffset.UTC));
    private final User ana = new User("Ana", "ana@gmail.com", "h", User.Role.USER);
    private final UUID family = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(ana, "id", 7L);
        when(accessTokens.issue(any())).thenReturn(new TokenService.IssuedToken("jwt", NOW.plusSeconds(900)));
        when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private RefreshToken stored(Instant createdAt, Instant usedAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken(ana, family, SessionService.hash(TOKEN), createdAt,
                createdAt.plus(Duration.ofDays(14)));
        ReflectionTestUtils.setField(token, "usedAt", usedAt);
        ReflectionTestUtils.setField(token, "revokedAt", revokedAt);
        return token;
    }

    @Test
    void aLoginStartsANewFamilyAndStoresOnlyTheHash() {
        when(users.findByUsername("Ana")).thenReturn(Optional.of(ana));

        AuthResponse session = sessions.start("Ana");

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(session.token()).isEqualTo("jwt");
        assertThat(session.refreshToken()).matches("[A-Za-z0-9_-]{43}");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(SessionService.hash(session.refreshToken()))
                .doesNotContain(session.refreshToken());
        assertThat(session.refreshExpiresAt()).isEqualTo(NOW.plus(Duration.ofDays(14)));
        assertThat(session.user().username()).isEqualTo("Ana");
    }

    @Test
    void eachRefreshUsesUpTheTokenAndContinuesTheSameFamily() {
        when(tokens.use(SessionService.hash(TOKEN), NOW)).thenReturn(1);
        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(3600), NOW, null)));

        AuthResponse next = sessions.refresh(TOKEN);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(tokens).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(family);
        assertThat(next.refreshToken()).isNotEqualTo(TOKEN);
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void aTokenPresentedAgainRevealsACopyAndClosesTheWholeFamily() {
        when(tokens.use(anyString(), any())).thenReturn(0);
        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(7200), NOW.minusSeconds(600), null)));

        assertThatThrownBy(() -> sessions.refresh(TOKEN)).isInstanceOf(InvalidSessionException.class);
        verify(tokens).revokeFamily(family, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void theSameBrowserRetryingWithinTheGraceIsRefusedWithoutClosingTheSession() {
        when(tokens.use(anyString(), any())).thenReturn(0);
        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(7200), NOW.minusSeconds(3), null)));

        assertThatThrownBy(() -> sessions.refresh(TOKEN)).isInstanceOf(InvalidSessionException.class);
        verify(tokens, never()).revokeFamily(any(), any());
    }

    @Test
    void unknownExpiredRevokedOrMalformedTokensEndTheSession() {
        when(tokens.use(anyString(), any())).thenReturn(0);
        assertThatThrownBy(() -> sessions.refresh(TOKEN)).isInstanceOf(InvalidSessionException.class);

        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(7200), null, NOW.minusSeconds(60))));
        assertThatThrownBy(() -> sessions.refresh(TOKEN)).isInstanceOf(InvalidSessionException.class);

        for (String bad : new String[]{null, "", "short", "a".repeat(43) + "'", "a".repeat(10_000)}) {
            assertThatThrownBy(() -> sessions.refresh(bad)).isInstanceOf(InvalidSessionException.class);
        }
        verify(tokens, never()).revokeFamily(any(), any());
        verify(tokens, never()).save(any());
    }

    @Test
    void aPasswordChangeAfterTheLoginEndsThatSession() {
        ana.changePassword("h2", NOW.minusSeconds(60));
        when(tokens.use(anyString(), any())).thenReturn(1);
        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(3600), NOW, null)));

        assertThatThrownBy(() -> sessions.refresh(TOKEN)).isInstanceOf(InvalidSessionException.class);
        verify(tokens).revokeFamily(family, NOW);
        verify(tokens, never()).save(any());
    }

    @Test
    void signingOutRevokesTheFamilyAndIgnoresUnknownTokens() {
        when(tokens.findByTokenHash(SessionService.hash(TOKEN)))
                .thenReturn(Optional.of(stored(NOW.minusSeconds(60), null, null)));

        sessions.end(TOKEN);
        sessions.end(null);
        sessions.end("not-a-token");

        verify(tokens, times(1)).revokeFamily(eq(family), eq(NOW));
    }

    @Test
    void closingEverySessionOfAnAccountRevokesAllItsTokens() {
        when(tokens.revokeAllOf(7L, NOW)).thenReturn(3);

        assertThat(sessions.endAll(7L)).isEqualTo(3);
    }
}
