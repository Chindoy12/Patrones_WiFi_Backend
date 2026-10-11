package com.wifisense.network;

import com.wifisense.network.decorator.DataSourceMetrics;
import com.wifisense.network.decorator.InvalidSnapshotException;
import com.wifisense.network.decorator.MetricsDecorator;
import com.wifisense.network.decorator.ValidationDecorator;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.ZoneOffset;

import static com.wifisense.TestData.NOW;
import static com.wifisense.TestData.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DataSourceDecoratorTest {

    private static final NetworkTarget TARGET = new NetworkTarget(1, "Test", "A4:2B:B0:10:00:01", "5GHz");

    private final DataSourceMetrics metrics = new DataSourceMetrics(Clock.fixed(NOW, ZoneOffset.UTC));

    private static NetworkDataSource fixed(NetworkSnapshot snapshot) {
        return new NetworkDataSource() {
            public DataSourceType type() { return DataSourceType.SIMULATION; }
            public NetworkSnapshot collect(NetworkTarget target) { return snapshot; }
        };
    }

    @Test
    void validationRejectsImpossibleValues() {
        NetworkDataSource source = new ValidationDecorator(fixed(snapshot(20, 2, 140, -50, NOW)));

        assertThatThrownBy(() -> source.collect(TARGET))
                .isInstanceOf(InvalidSnapshotException.class)
                .hasMessageContaining("pérdida de paquetes");
    }

    @Test
    void validationPassesCorrectValuesUnchanged() {
        NetworkSnapshot reading = snapshot(20, 2, 0.5, -50, NOW);

        assertThat(new ValidationDecorator(fixed(reading)).collect(TARGET)).isSameAs(reading);
    }

    @Test
    void metricsCountSuccessesAndFailures() {
        new MetricsDecorator(fixed(snapshot(20, 2, 0.5, -50, NOW)), metrics).collect(TARGET);
        NetworkDataSource failing = new MetricsDecorator(new RouterApiDataSource(), metrics);

        assertThatThrownBy(() -> failing.collect(TARGET)).isInstanceOf(DataSourceUnavailableException.class);
        assertThat(metrics.snapshot())
                .extracting(DataSourceMetrics.Snapshot::type, DataSourceMetrics.Snapshot::successes,
                        DataSourceMetrics.Snapshot::failures)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(DataSourceType.SIMULATION, 1L, 0L),
                        org.assertj.core.groups.Tuple.tuple(DataSourceType.ROUTER_API, 0L, 1L));
    }

    @Test
    void decoratorsKeepTheWrappedType() {
        NetworkDataSource chain = new MetricsDecorator(new ValidationDecorator(new SnmpDataSource()), metrics);

        assertThat(chain.type()).isEqualTo(DataSourceType.SNMP);
    }
}
