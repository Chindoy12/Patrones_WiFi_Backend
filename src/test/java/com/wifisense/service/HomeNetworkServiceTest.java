package com.wifisense.service;

import com.wifisense.dto.HomeNetworkRequest;
import com.wifisense.model.HomeNetwork;
import com.wifisense.model.User;
import com.wifisense.repository.HomeNetworkRepository;
import com.wifisense.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HomeNetworkServiceTest {

    private final HomeNetworkRepository networks = mock(HomeNetworkRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final HomeNetworkService service = new HomeNetworkService(networks, users);
    private final User ana = new User("ana", "ana@wifisense.local", "hash", User.Role.USER);

    @Test
    void createsANetworkForTheOwnerWithCleanValues() {
        when(users.findByUsername("ana")).thenReturn(Optional.of(ana));
        when(networks.save(any(HomeNetwork.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var created = service.create("ana", new HomeNetworkRequest("  Casa  ", "  ", 300));

        assertThat(created.name()).isEqualTo("Casa");
        assertThat(created.provider()).isNull();
        assertThat(created.contractedMbps()).isEqualTo(300);
        verify(networks).save(argThat(network -> network.getOwner() == ana));
    }

    @Test
    void aUserCannotRegisterMoreThanThreeNetworks() {
        when(networks.countByOwner_Username("ana")).thenReturn(3L);

        assertThatThrownBy(() -> service.create("ana", new HomeNetworkRequest("Oficina", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hasta 3 redes");
        verify(networks, never()).save(any());
    }

    @Test
    void namesAreUniquePerOwner() {
        when(networks.existsByOwner_UsernameAndNameIgnoreCase("ana", "casa")).thenReturn(true);

        assertThatThrownBy(() -> service.create("ana", new HomeNetworkRequest("casa", null, null)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Ya tienes una red con ese nombre.");
    }

    @Test
    void someoneElsesNetworkLooksLikeItDoesNotExist() {
        when(networks.findByIdAndOwner_Username(5L, "intruso")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete("intruso", 5L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("No se encontró la red con id 5");
        assertThatThrownBy(() -> service.update("intruso", 5L, new HomeNetworkRequest("Mía", null, null)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(networks, never()).delete(any());
    }

    @Test
    void renamingKeepsTheSameNameAllowed() {
        HomeNetwork casa = new HomeNetwork(ana, "Casa", null, null);
        when(networks.findByIdAndOwner_Username(1L, "ana")).thenReturn(Optional.of(casa));

        var updated = service.update("ana", 1L, new HomeNetworkRequest("CASA", "Claro", 500));

        assertThat(updated.name()).isEqualTo("CASA");
        assertThat(updated.provider()).isEqualTo("Claro");
    }
}
