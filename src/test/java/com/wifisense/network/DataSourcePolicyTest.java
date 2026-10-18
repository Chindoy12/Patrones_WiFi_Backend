package com.wifisense.network;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourcePolicyTest {

    private static DataSourcePolicy policy(boolean demo) {
        return new DataSourcePolicy(new DataSourceProperties(new DataSourceProperties.Simulation(0.1),
                new DataSourceProperties.SystemProbe("1.1.1.1", 443, 5, Duration.ofSeconds(1)), demo));
    }

    @Test
    void inProductionANetworkIsOnlyMeasuredByItsSensor() {
        DataSourcePolicy production = policy(false);

        assertThat(production.selectable()).containsExactly(DataSourceType.AGENT);
        assertThatNoException().isThrownBy(() -> production.requireSelectable(DataSourceType.AGENT));
        assertThatThrownBy(() -> production.requireSelectable(DataSourceType.SIMULATION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Las redes simuladas están desactivadas: registra la red con su sensor WiFiSense.");
        assertThatThrownBy(() -> production.requireSelectable(DataSourceType.SNMP))
                .hasMessageStartingWith("Esa fuente de datos aún no está disponible");
        // nothing is collected by the backend: the sensors send their readings
        for (DataSourceType type : DataSourceType.values()) {
            assertThat(production.collectsAutomatically(type)).isFalse();
        }
    }

    @Test
    void aLocalDemoCanStillUseSimulatedNetworks() {
        DataSourcePolicy demo = policy(true);

        assertThat(demo.selectable()).containsExactly(DataSourceType.AGENT, DataSourceType.SIMULATION,
                DataSourceType.SYSTEM);
        assertThat(demo.collectsAutomatically(DataSourceType.SIMULATION)).isTrue();
        assertThat(demo.collectsAutomatically(DataSourceType.AGENT)).isFalse();
        assertThatThrownBy(() -> demo.requireSelectable(DataSourceType.TRAFFIC_CAPTURE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
