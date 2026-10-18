package com.wifisense.integration;

import com.wifisense.dto.MetricSummary;
import com.wifisense.model.RefreshToken;
import com.wifisense.model.User;
import com.wifisense.repository.MeasurementStatsRepository;
import com.wifisense.repository.RefreshTokenRepository;
import com.wifisense.repository.UserRepository;
import com.wifisense.security.SessionService;
import com.wifisense.service.MonitoringRetentionJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The queries written by hand (JPQL updates, native SQL) against the real schema. */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
class DatabaseQueriesIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private TransactionTemplate transaction;
    @Autowired
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private SessionService sessions;
    @Autowired
    private MeasurementStatsRepository stats;
    @Autowired
    private MonitoringRetentionJob retention;

    private final Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    @Test
    void accountsAreFoundRegardlessOfCaseAndEmailsAreStoredInLowercase() {
        String name = "Carlos.It" + unique();
        users.save(new User(name, name + "@Ejemplo.com", "hash", User.Role.USER));

        assertThat(users.findByUsernameIgnoreCase(name.toUpperCase())).map(User::getUsername).contains(name);
        assertThat(users.existsByUsernameIgnoreCase(name.toLowerCase())).isTrue();
        assertThat(users.findByUsername(name.toLowerCase())).isEmpty();
        assertThat(users.findByEmailIgnoreCase(name.toUpperCase() + "@EJEMPLO.COM")).isPresent();
        assertThat(jdbc.queryForObject("SELECT email FROM users WHERE username = ?", String.class, name))
                .isEqualTo(name.toLowerCase() + "@ejemplo.com");
    }

    @Test
    void aRefreshTokenIsUsedUpByExactlyOneRequest() {
        User user = users.save(new User("Dora.It" + unique(), "dora" + unique() + "@ejemplo.com", "h", User.Role.USER));
        String hash = randomHash();
        refreshTokens.save(new RefreshToken(user, UUID.randomUUID(), hash, now, now.plus(Duration.ofDays(1))));

        Integer first = transaction.execute(status -> refreshTokens.use(hash, now.plusSeconds(1)));
        Integer second = transaction.execute(status -> refreshTokens.use(hash, now.plusSeconds(2)));
        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        // expired tokens cannot be used either, and the nightly cleanup removes them
        String expired = randomHash();
        refreshTokens.save(new RefreshToken(user, UUID.randomUUID(), expired, now.minus(Duration.ofDays(20)),
                now.minus(Duration.ofDays(6))));
        Integer late = transaction.execute(status -> refreshTokens.use(expired, now));
        assertThat(late).isZero();
        assertThat(sessions.purgeExpired()).isGreaterThanOrEqualTo(1);
        assertThat(refreshTokens.findByTokenHash(expired)).isEmpty();
        assertThat(refreshTokens.findByTokenHash(hash)).isPresent();
    }

    @Test
    void reportStatisticsFromPostgresMatchTheOnesComputedInJava() {
        long network = network();
        Random random = new Random(7);
        Instant from = now.minus(Duration.ofDays(2));
        Instant to = now.minus(Duration.ofDays(1));
        for (int i = 0; i < 300; i++) {
            Instant at = from.minus(Duration.ofHours(6)).plusSeconds(i * 360L);  // some fall outside the period
            jdbc.update("INSERT INTO measurements (network_id, latency_ms, jitter_ms, packet_loss_pct, bandwidth_mbps,"
                            + " signal_strength_dbm, source, measured_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    network, 5 + random.nextDouble() * 200, random.nextDouble() * 30, random.nextDouble() * 5,
                    i % 7 == 0 ? null : random.nextDouble() * 300, i % 5 == 0 ? null : -30 - random.nextInt(60),
                    i % 50 == 0 ? "SIMULATION" : "SYSTEM", Timestamp.from(at));
        }
        List<Double> latency = new ArrayList<>();
        List<Double> bandwidth = new ArrayList<>();
        List<Integer> signal = new ArrayList<>();
        jdbc.query("SELECT latency_ms, bandwidth_mbps, signal_strength_dbm FROM measurements WHERE network_id = ? "
                + "AND measured_at BETWEEN ? AND ?", rs -> {
            latency.add(rs.getDouble(1));
            bandwidth.add((Double) rs.getObject(2));
            signal.add((Integer) rs.getObject(3));
        }, network, Timestamp.from(from), Timestamp.from(to));

        MeasurementStatsRepository.Stats result = stats.summarize(network, from, to);

        assertThat(result.samples()).isEqualTo(latency.size()).isBetween(200L, 300L);
        assertThat(result.latency()).isEqualTo(MetricSummary.of(latency));
        assertThat(result.bandwidth()).isEqualTo(MetricSummary.of(bandwidth));
        assertThat(result.signal()).isEqualTo(MetricSummary.of(signal));
        assertThat(stats.summarize(network, now, now.plusSeconds(60)).latency().samples()).isZero();
    }

    @Test
    void theNightlyCleanupDeletesOnlyOldMonitoringDataAndOpenAlertsStay() {
        long network = network();
        Timestamp old = Timestamp.from(now.minus(Duration.ofDays(100)));
        Timestamp recent = Timestamp.from(now.minus(Duration.ofDays(10)));
        for (int i = 0; i < 230; i++) {  // more than two batches of 100
            measurement(network, old);
        }
        measurement(network, recent);
        jdbc.update("INSERT INTO traffic_observations (network_id, traffic_volume_mb, packet_count, observed_at) "
                + "VALUES (?, 1, 10, ?), (?, 1, 10, ?)", network, old, network, recent);
        jdbc.update("INSERT INTO protocol_statistics (network_id, protocol, packet_count, period_start, period_end) "
                + "VALUES (?, 'TCP', 5, ?, ?), (?, 'TCP', 5, ?, ?)", network, old, old, network, recent, recent);
        long device = jdbc.queryForObject("INSERT INTO devices (network_id, ip_address, mac_address) VALUES (?, "
                + "'10.0.0.9', ?) RETURNING id", Long.class, network, randomMac());
        jdbc.update("INSERT INTO traffic_sessions (device_id, protocol, started_at) VALUES (?, 'TCP', ?), "
                + "(?, 'TCP', ?)", device, old, device, recent);
        long oldAnalysis = jdbc.queryForObject("INSERT INTO analysis_results (network_id, analysis_type, "
                + "detected_status, score, summary, measurement_count, created_at) VALUES (?, 'THRESHOLD', 'WARNING',"
                + " 0.5, 'prueba', 3, ?) RETURNING id", Long.class, network, old);
        jdbc.update("INSERT INTO ai_predictions (analysis_result_id, network_id, anomaly_detected, anomaly_score, "
                + "severity, message, model_version, simulated_data, created_at) VALUES (?, ?, false, 0.1, 'LOW', "
                + "'prueba', 'it', false, ?)", oldAnalysis, network, old);
        long openAlert = jdbc.queryForObject("INSERT INTO alerts (network_id, analysis_result_id, severity, message, "
                + "created_at) VALUES (?, ?, 'WARNING', 'abierta', ?) RETURNING id", Long.class, network, oldAnalysis,
                old);
        jdbc.update("INSERT INTO alerts (network_id, severity, message, status, created_at, resolved_at) VALUES "
                + "(?, 'INFO', 'vieja', 'RESOLVED', ?, ?), (?, 'INFO', 'reciente', 'RESOLVED', ?, ?)",
                network, old, old, network, recent, recent);

        var deleted = retention.purgeOlderThan(now.minus(Duration.ofDays(90)));

        assertThat(deleted.get("measurements")).isGreaterThanOrEqualTo(230);
        assertThat(count("measurements", network)).isEqualTo(1);
        assertThat(count("traffic_observations", network)).isEqualTo(1);
        assertThat(count("protocol_statistics", network)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM traffic_sessions WHERE device_id = ?", Long.class, device))
                .isEqualTo(1);
        assertThat(count("analysis_results", network)).isZero();
        assertThat(count("ai_predictions", network)).isZero();
        // the open alert stays (someone has to look at it); it only loses the link to the deleted analysis
        assertThat(jdbc.queryForList("SELECT message FROM alerts WHERE network_id = ? ORDER BY message", String.class,
                network)).containsExactly("abierta", "reciente");
        assertThat(jdbc.queryForObject("SELECT analysis_result_id FROM alerts WHERE id = ?", Long.class, openAlert))
                .isNull();
    }

    private long network() {
        String name = "Sede IT " + unique();
        long location = jdbc.queryForObject("INSERT INTO locations (name) VALUES (?) RETURNING id", Long.class, name);
        long zone = jdbc.queryForObject("INSERT INTO zones (location_id, name) VALUES (?, 'Piso 1') RETURNING id",
                Long.class, location);
        return jdbc.queryForObject("INSERT INTO networks (zone_id, ssid, bssid, frequency_band, channel, security_type)"
                + " VALUES (?, ?, ?, '5GHz', 36, 'WPA3') RETURNING id", Long.class, zone, name, randomMac());
    }

    private void measurement(long network, Timestamp at) {
        jdbc.update("INSERT INTO measurements (network_id, latency_ms, jitter_ms, packet_loss_pct, source, measured_at)"
                + " VALUES (?, 20, 2, 0, 'SYSTEM', ?)", network, at);
    }

    private long count(String table, long network) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE network_id = ?", Long.class, network);
    }

    private static String randomHash() {
        byte[] bytes = new byte[32];
        new Random().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static String randomMac() {
        byte[] bytes = new byte[6];
        new Random().nextBytes(bytes);
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes);
    }
}
