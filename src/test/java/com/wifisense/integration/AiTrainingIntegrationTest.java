package com.wifisense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.dto.AiTrainingStatus;
import com.wifisense.repository.NetworkSensorRepository;
import com.wifisense.service.AiTrainingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The SQL of Avance 16 against the real schema: the real measurements that train the AI, the history of model
 * versions, and rebuilding the active version in the AI service after it restarts. The AI itself is a stub here.
 */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class AiTrainingIntegrationTest extends PostgresIntegrationTest {

    // In 2031, so no measurement of another test falls in the 30 days of these trainings.
    private static final Instant NOW = Instant.parse("2031-03-10T15:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private NetworkSensorRepository sensors;

    private final ObjectMapper json = new ObjectMapper();
    private final AiAnalysisClient ai = mock(AiAnalysisClient.class);
    private Long network;

    private AiTrainingService service() {
        return new AiTrainingService(jdbc, ai, json, transaction, Clock.fixed(NOW, ZoneOffset.UTC),
                "0 0 3 * * MON", "America/Bogota", true);
    }

    private long teamNetwork() {
        String place = "Sede IA " + unique();
        jdbc.update("INSERT INTO locations (name) VALUES (?)", place);
        jdbc.update("INSERT INTO zones (location_id, name) SELECT id, 'Piso' FROM locations WHERE name = ?", place);
        byte[] mac = new byte[5];
        new Random().nextBytes(mac);
        String bssid = String.format("02:%02X:%02X:%02X:%02X:%02X", mac[0], mac[1], mac[2], mac[3], mac[4]);
        return jdbc.queryForObject("INSERT INTO networks (zone_id, ssid, bssid, frequency_band, channel, security_type) "
                + "SELECT z.id, ?, ?, '5GHz', 36, 'WPA2' FROM zones z JOIN locations l ON l.id = z.location_id "
                + "WHERE l.name = ? RETURNING id", Long.class, "Red IA " + unique(), bssid, place);
    }

    private void measurements(long networkId, int count) {
        Random random = new Random(3);
        for (int i = 0; i < count; i++) {
            Timestamp at = Timestamp.from(NOW.minus(Duration.ofMinutes(5L * (count - i))));
            jdbc.update("INSERT INTO measurements (network_id, latency_ms, jitter_ms, packet_loss_pct, bandwidth_mbps, "
                            + "signal_strength_dbm, connected_devices, source, measured_at) VALUES (?, ?, ?, ?, NULL, ?, ?, "
                            + "'AGENT', ?)", networkId, 30 + random.nextDouble() * 20, 2 + random.nextDouble() * 3,
                    random.nextDouble(), -55 - random.nextInt(10), 3 + random.nextInt(6), at);
            if (i % 2 == 0) {
                jdbc.update("INSERT INTO traffic_observations (network_id, traffic_volume_mb, packet_count, observed_at) "
                        + "VALUES (?, ?, ?, ?)", networkId, 50 + random.nextDouble() * 30, 5000 + random.nextInt(2000), at);
            }
        }
        // a simulated measurement is never used to train
        jdbc.update("INSERT INTO measurements (network_id, latency_ms, jitter_ms, packet_loss_pct, source, measured_at) "
                + "VALUES (?, 900, 90, 50, 'SIMULATION', ?)", networkId, Timestamp.from(NOW.minusSeconds(30)));
    }

    private JsonNode aiAnswer(String decision, boolean active) throws Exception {
        return json.readTree("""
                {"version":"x","samples":320,"trainSamples":256,"testNormal":64,"testFaults":64,
                 "decision":"%s","reason":"Da menos falsas alarmas con estas redes.","active":%s,
                 "candidate":{"precision":0.95,"recall":0.9,"f1":0.92,"falsePositiveRate":0.03},
                 "current":{"precision":0.8,"recall":0.9,"f1":0.85,"falsePositiveRate":0.2},
                 "currentVersion":"isolation-forest-1.1"}""".formatted(decision, active));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM ai_model_versions WHERE version LIKE 'real-2031%'");
        if (network != null) {
            jdbc.update("DELETE FROM networks WHERE id = ?", network);
        }
    }

    @Test
    void trainsWithTheRealMeasurementsAndKeepsTheHistory() throws Exception {
        network = teamNetwork();
        AiTrainingService service = service();
        measurements(network, 150);
        assertThat(service.status().canTrain()).isFalse();
        assertThatThrownBy(() -> service.train(ADMIN)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("al menos 300");

        measurements(network, 170);  // 320 real measurements now
        AiTrainingStatus status = service.status();
        assertThat(status.canTrain()).isTrue();
        assertThat(status.available().teamSamples()).isEqualTo(320);
        assertThat(status.available().to()).isBefore(NOW);
        assertThat(status.nextAutomatic()).isEqualTo(Instant.parse("2031-03-17T08:00:00Z"));  // Monday 3:00 Bogotá

        when(ai.trainModel(any())).thenReturn(aiAnswer("PROMOTED", true));
        AiTrainingStatus.Version first = service.train(ADMIN);

        ArgumentCaptor<JsonNode> sent = ArgumentCaptor.forClass(JsonNode.class);
        verify(ai).trainModel(sent.capture());
        JsonNode body = sent.getValue();
        assertThat(body.path("restore").asBoolean()).isFalse();
        assertThat(body.path("samples")).hasSize(320);
        JsonNode sample = body.path("samples").get(0);
        assertThat(sample.has("latency") && sample.has("signalStrength") && sample.has("trafficVolume")).isTrue();
        assertThat(sample.has("bandwidth")).isFalse();  // not measured: the AI fills it
        assertThat(sample.has("networkId") || sample.has("network")).isFalse();

        assertThat(first.version()).isEqualTo("real-20310310-1500");
        assertThat(first.decision()).isEqualTo("PROMOTED");
        assertThat(first.active()).isTrue();
        assertThat(first.trainedBy()).isEqualTo(ADMIN);
        assertThat(first.samples()).isEqualTo(320);
        assertThat(first.networks()).isEqualTo(1);
        assertThat(first.metrics().path("candidate").path("f1").asDouble()).isEqualTo(0.92);

        when(ai.trainModel(any())).thenReturn(aiAnswer("REJECTED", false));
        AiTrainingStatus.Version second = service.train(null);
        assertThat(second.version()).startsWith("real-20310310-1500-");
        assertThat(second.active()).isFalse();
        assertThat(second.trainedBy()).isNull();
        assertThat(service.status().versions()).extracting(AiTrainingStatus.Version::version)
                .startsWith(second.version(), first.version());

        // the AI restarted with its synthetic model: the active version is rebuilt from the same measurements
        reset(ai);
        when(ai.modelInfo()).thenReturn(json.readTree("{\"modelVersion\":\"isolation-forest-1.1\"}"));
        service.syncActiveModel();
        verify(ai).trainModel(sent.capture());
        assertThat(sent.getValue().path("restore").asBoolean()).isTrue();
        assertThat(sent.getValue().path("version").asText()).isEqualTo(first.version());
        assertThat(sent.getValue().path("samples")).isEqualTo(body.path("samples"));

        reset(ai);
        when(ai.modelInfo()).thenReturn(json.readTree("{\"modelVersion\":\"" + first.version() + "\"}"));
        service.syncActiveModel();
        verify(ai, never()).trainModel(any());
    }

    @Test
    void identifyModeNeedsAnInstalledSensor() {
        network = teamNetwork();
        Integer updated = transaction.execute(s -> sensors.identifyUntil(network, NOW));
        assertThat(updated).isZero();
    }
}
