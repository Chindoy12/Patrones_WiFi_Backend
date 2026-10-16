package com.wifisense.service;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.dto.AgentPairResponse;
import com.wifisense.dto.AgentPairingCodeResponse;
import com.wifisense.model.AgentPairingCode;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentPairingCodeRepository;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.security.RecoveryBlockedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentPairingServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");

    private final AgentPairingCodeRepository codes = mock(AgentPairingCodeRepository.class);
    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentKeyService keys = mock(AgentKeyService.class);
    private final AgentPairingService service = new AgentPairingService(codes, networks, keys,
            Clock.fixed(NOW, ZoneOffset.UTC));
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", null, null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(casa, "id", 7L);
        when(networks.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(casa));
        when(keys.issue(7L)).thenReturn(new AgentKeyCreated("wsa_" + "k".repeat(43), "wsa_kkkk", NOW));
    }

    @Test
    void createsAReadableCodeAndStoresOnlyItsHash() {
        AgentPairingCodeResponse created = service.create("ana", 7L);

        assertThat(created.code()).matches("^[A-HJKMNP-Z2-9]{4}-[A-HJKMNP-Z2-9]{4}$");
        assertThat(created.expiresAt()).isEqualTo(NOW.plus(AgentPairingService.TTL));
        ArgumentCaptor<AgentPairingCode> stored = ArgumentCaptor.forClass(AgentPairingCode.class);
        verify(codes).save(stored.capture());
        assertThat(stored.getValue().getCodeHash())
                .isEqualTo(AgentPairingService.hash(created.code().replace("-", "")));
        verify(codes).deleteByNetworkId(7L);
    }

    @Test
    void someoneElsesNetworkGetsNoCode() {
        assertThatThrownBy(() -> service.create("eva", 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(codes, never()).save(any());
    }

    @Test
    void aValidCodeIsSpentAndBecomesTheAgentKey() {
        AgentPairingCode pending = new AgentPairingCode(casa, AgentPairingService.hash("K7QM4RZD"), NOW, NOW.plusSeconds(600));
        when(codes.findByCodeHash(AgentPairingService.hash("K7QM4RZD"))).thenReturn(Optional.of(pending));

        AgentPairResponse paired = service.pair(" k7qm-4rzd ", "1.2.3.4");

        assertThat(paired.key()).startsWith("wsa_");
        assertThat(paired.networkName()).isEqualTo("Casa");
        verify(codes).delete(pending);
        verify(keys).issue(7L);
    }

    @Test
    void expiredUnknownOrMalformedCodesFailTheSameWay() {
        AgentPairingCode old = new AgentPairingCode(casa, AgentPairingService.hash("AAAABBBB"), NOW.minusSeconds(900), NOW);
        when(codes.findByCodeHash(AgentPairingService.hash("AAAABBBB"))).thenReturn(Optional.of(old));

        for (String code : new String[]{"AAAA-BBBB", "ZZZZ-ZZZZ", "0000-1111", "corto", null}) {
            assertThatThrownBy(() -> service.pair(code, "1.2.3.4")).isInstanceOf(InvalidPairingCodeException.class);
        }
        verify(keys, never()).issue(any());
    }

    @Test
    void guessingCodesFromOneAddressIsStopped() {
        for (int i = 0; i < AgentPairingService.MAX_ATTEMPTS; i++) {
            assertThatThrownBy(() -> service.pair("ZZZZ-ZZZZ", "6.6.6.6")).isInstanceOf(InvalidPairingCodeException.class);
        }
        assertThatThrownBy(() -> service.pair("ZZZZ-ZZZZ", "6.6.6.6")).isInstanceOf(RecoveryBlockedException.class);
    }

    @Test
    void normalizesWhatPeopleType() {
        assertThat(AgentPairingService.normalize("k7qm 4rzd")).isEqualTo("K7QM4RZD");
        assertThat(AgentPairingService.normalize("K7QM-4RZDX")).isNull();
        assertThat(AgentPairingService.normalize("O0O0-1I1I")).isNull();
    }
}
