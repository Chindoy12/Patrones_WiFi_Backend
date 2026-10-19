package com.wifisense.service;

import com.wifisense.dto.AgentKeyCreated;
import com.wifisense.model.AgentKey;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.AgentKeyRepository;
import com.wifisense.repository.HomeNetworkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentKeyServiceTest {

    private final AgentKeyRepository keys = mock(AgentKeyRepository.class);
    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final AgentReportThrottle throttle = mock(AgentReportThrottle.class);
    private final AgentKeyService service = new AgentKeyService(keys, networks, throttle,
            java.time.Clock.fixed(java.time.Instant.parse("2026-10-19T15:00:00Z"), java.time.ZoneOffset.UTC));
    private final HomeNetwork casa = new HomeNetwork(mock(User.class), "Casa", null, null);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(casa, "id", 7L);
        when(networks.findByIdAndOwner_Username(7L, "ana")).thenReturn(Optional.of(casa));
        when(networks.getReferenceById(7L)).thenReturn(casa);
        when(keys.saveAndFlush(any(AgentKey.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createsARandomKeyAndStoresOnlyItsHash() {
        AgentKeyCreated created = service.create("ana", 7L);

        ArgumentCaptor<AgentKey> stored = ArgumentCaptor.forClass(AgentKey.class);
        verify(keys).saveAndFlush(stored.capture());
        assertThat(created.key()).matches("^wsa_[A-Za-z0-9_-]{43}$");
        assertThat(created.prefix()).isEqualTo(created.key().substring(0, 8));
        assertThat(stored.getValue().getKeyHash()).matches("^[0-9a-f]{64}$").isEqualTo(AgentKeyService.hash(created.key()));
        assertThat(stored.getValue().getKeyHash()).doesNotContain(created.key().substring(4));
        assertThat(service.create("ana", 7L).key()).isNotEqualTo(created.key());
    }

    @Test
    void creatingAKeyRevokesThePreviousOne() {
        AgentKey old = new AgentKey(casa, "a".repeat(64), "wsa_old1");
        ReflectionTestUtils.setField(old, "id", 3L);
        when(keys.findByNetworkId(7L)).thenReturn(Optional.of(old));

        service.create("ana", 7L);

        verify(keys).deleteByNetworkId(7L);
        verify(throttle).forget(3L);
    }

    @Test
    void someoneElsesNetworkLooksLikeOneThatDoesNotExist() {
        assertThatThrownBy(() -> service.create("eva", 7L)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> service.revoke("eva", 7L)).isInstanceOf(ResourceNotFoundException.class);
        verify(keys, never()).deleteByNetworkId(any());
    }

    @Test
    void authenticatesByHashAndRejectsEverythingElseTheSameWay() {
        String key = "wsa_" + "A".repeat(43);
        AgentKey stored = new AgentKey(casa, AgentKeyService.hash(key), "wsa_AAAA");
        when(keys.findByKeyHash(AgentKeyService.hash(key))).thenReturn(Optional.of(stored));

        assertThat(service.authenticate(key)).isSameAs(stored);
        for (String bad : new String[]{null, "", "wsa_corta", "Bearer x", "wsa_" + "B".repeat(43)}) {
            assertThatThrownBy(() -> service.authenticate(bad)).isInstanceOf(InvalidAgentKeyException.class)
                    .hasMessage("La clave del agente no es válida o fue revocada.");
        }
    }
}
