package com.wifisense.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.wifisense.analysis.AiAnalysisClient;
import com.wifisense.analysis.AiServiceUnavailableException;
import com.wifisense.dto.AiTrainingStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Trains the anomaly model of the AI with the real measurements of the networks and keeps the history of versions.
 *
 * <p>The AI service cannot read the database, so the backend sends it the measurements: those of the sensors of the
 * team networks (source AGENT, with their traffic) and those of the agents of the users' homes. Only the numbers
 * travel and are kept (latency, jitter, loss, bandwidth, signal, traffic, devices, packets): no network, user or
 * device. The AI evaluates the new model against the one in use and replaces it only if it is better; the backend
 * records the result in ai_model_versions.
 *
 * <p>The AI service keeps its model on a disk that is lost when it is redeployed, so a job checks that it uses the
 * active version and, if not, sends it the same measurements to rebuild it (same data and seed, same model).
 */
@Service
public class AiTrainingService {

    public static final int MINIMUM_SAMPLES = 300;
    static final int MAXIMUM_SAMPLES = 10_000;
    static final Duration WINDOW = Duration.ofDays(30);
    private static final int SEED = 42;
    private static final Logger log = LoggerFactory.getLogger(AiTrainingService.class);
    private static final DateTimeFormatter VERSION_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    /** The features in the order the training set stores them (the AI's names). */
    static final List<String> FEATURES = List.of("latency", "jitter", "packetLoss", "bandwidth", "signalStrength",
            "trafficVolume", "connectedDevices", "packetCount");

    private final JdbcTemplate jdbc;
    private final AiAnalysisClient ai;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String cron;
    private final String zone;
    private final boolean automatic;

    public AiTrainingService(JdbcTemplate jdbc, AiAnalysisClient ai, ObjectMapper json, TransactionTemplate transaction,
                             Clock clock,
                             @Value("${wifisense.ai.training.cron:0 0 3 * * MON}") String cron,
                             @Value("${wifisense.ai.training.zone:America/Bogota}") String zone,
                             @Value("${wifisense.ai.training.automatic:true}") boolean automatic) {
        this.jdbc = jdbc;
        this.ai = ai;
        this.json = json;
        this.transaction = transaction;
        this.clock = clock;
        this.cron = cron;
        this.zone = zone;
        this.automatic = automatic;
    }

    /** One real measurement: its time, where it comes from (to count networks) and the eight features. */
    record Sample(Instant at, String network, Double[] values) {
    }

    public AiTrainingStatus status() {
        List<Sample> samples = dataset();
        return new AiTrainingStatus(summary(samples), MINIMUM_SAMPLES, samples.size() >= MINIMUM_SAMPLES,
                nextAutomatic(), versions());
    }

    /**
     * Trains now. {@code username} is the administrator who asked, or null for the weekly training. Fails with a
     * message when there are not enough measurements, and when the AI service does not answer.
     */
    public synchronized AiTrainingStatus.Version train(String username) {
        List<Sample> samples = dataset();
        if (samples.size() < MINIMUM_SAMPLES) {
            throw new IllegalStateException("Se necesitan al menos " + MINIMUM_SAMPLES + " mediciones reales para "
                    + "entrenar y hay " + samples.size() + ". Deja los sensores y agentes encendidos unos días más.");
        }
        Instant now = Instant.now(clock);
        String version = "real-" + VERSION_TIME.format(now.atZone(ZoneOffset.UTC));
        if (versionExists(version)) {
            version = version + "-" + (countVersions() + 1);
        }
        ArrayNode trainingSet = trainingSet(samples);
        JsonNode result = ai.trainModel(request(version, trainingSet, false));
        String decision = "PROMOTED".equals(result.path("decision").asText()) ? "PROMOTED" : "REJECTED";
        boolean active = decision.equals("PROMOTED") && result.path("active").asBoolean(false);
        String reason = trim(result.path("reason").asText("Sin detalle."), 300);
        ObjectNode metrics = json.createObjectNode();
        metrics.set("candidate", result.path("candidate"));
        metrics.set("current", result.path("current"));
        metrics.put("currentVersion", result.path("currentVersion").asText(null));
        metrics.put("trainSamples", result.path("trainSamples").asInt());
        metrics.put("testNormal", result.path("testNormal").asInt());
        metrics.put("testFaults", result.path("testFaults").asInt());
        AiTrainingStatus.Data data = summary(samples);
        String finalVersion = version;
        Long id = transaction.execute(status -> {
            if (active) {
                jdbc.update("UPDATE ai_model_versions SET active = FALSE WHERE active");
            }
            return jdbc.queryForObject("""
                    INSERT INTO ai_model_versions (version, algorithm, trained_at, trained_by, samples, networks,
                        data_from, data_to, metrics, decision, reason, active, training_set)
                    VALUES (?, 'IsolationForest', ?, (SELECT id FROM users WHERE username = ?), ?, ?, ?, ?,
                        CAST(? AS jsonb), ?, ?, ?, CAST(? AS jsonb))
                    RETURNING id""", Long.class, finalVersion, Timestamp.from(now), username, data.samples(),
                    data.networks(), Timestamp.from(data.from()), Timestamp.from(data.to()), metrics.toString(),
                    decision, reason, active, trainingSet.toString());
        });
        log.info("AI model {} trained with {} measurements: {}", finalVersion, data.samples(), decision);
        return versions().stream().filter(v -> v.id().equals(id)).findFirst().orElseThrow();
    }

    /** Weekly training (Monday 3:00 Colombia time by default). Never throws: a failure is logged and retried next week. */
    @Scheduled(cron = "${wifisense.ai.training.cron:0 0 3 * * MON}", zone = "${wifisense.ai.training.zone:America/Bogota}")
    public void weekly() {
        if (!automatic) {
            return;
        }
        try {
            if (dataset().size() >= MINIMUM_SAMPLES) {
                train(null);
            } else {
                log.info("Weekly AI training skipped: not enough real measurements yet");
            }
        } catch (RuntimeException e) {
            log.warn("Weekly AI training failed: {}", e.getMessage());
        }
    }

    /**
     * Keeps the AI service on the active version: after it is redeployed it starts again with the synthetic model, so
     * the active version is rebuilt from its stored measurements. Never throws.
     */
    @Scheduled(fixedDelayString = "${wifisense.ai.training.sync:PT10M}", initialDelayString = "PT2M")
    public void syncActiveModel() {
        try {
            List<String[]> active = jdbc.query("SELECT version, training_set::text FROM ai_model_versions WHERE active",
                    (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});
            if (active.isEmpty()) {
                return;
            }
            String version = active.get(0)[0];
            if (version.equals(ai.modelInfo().path("modelVersion").asText())) {
                return;
            }
            ai.trainModel(request(version, (ArrayNode) json.readTree(active.get(0)[1]), true));
            log.info("AI model {} restored in the AI service", version);
        } catch (AiServiceUnavailableException e) {
            log.debug("AI service not available to restore the model: {}", e.getMessage());
        } catch (JsonProcessingException | RuntimeException e) {
            log.warn("Could not restore the active AI model: {}", e.getMessage());
        }
    }

    /** The training request: the stored rows turned back into the AI's samples. */
    private ObjectNode request(String version, ArrayNode trainingSet, boolean restore) {
        ObjectNode body = json.createObjectNode();
        body.put("version", version);
        body.put("seed", SEED);
        body.put("activate", true);
        body.put("restore", restore);
        ArrayNode list = body.putArray("samples");
        for (JsonNode row : trainingSet) {
            ObjectNode sample = list.addObject();
            for (int i = 0; i < FEATURES.size(); i++) {
                JsonNode value = row.path(i);
                if (!value.isMissingNode() && !value.isNull()) {
                    sample.set(FEATURES.get(i), value);
                }
            }
        }
        return body;
    }

    /** Rows of eight numbers (null where not measured), oldest first: all the AI needs to rebuild the model. */
    private ArrayNode trainingSet(List<Sample> samples) {
        ArrayNode rows = json.createArrayNode();
        for (Sample sample : samples) {
            ArrayNode row = rows.addArray();
            for (Double value : sample.values()) {
                if (value == null) {
                    row.addNull();
                } else {
                    row.add(Math.round(value * 1000.0) / 1000.0);
                }
            }
        }
        return rows;
    }

    /** The real measurements of the last 30 days (at most the latest 10 000), oldest first. */
    List<Sample> dataset() {
        Timestamp since = Timestamp.from(Instant.now(clock).minus(WINDOW));
        List<Sample> samples = new ArrayList<>(jdbc.query("""
                SELECT * FROM (
                    SELECT m.measured_at AS at, 'T' || m.network_id AS network, m.latency_ms, m.jitter_ms,
                           m.packet_loss_pct, m.bandwidth_mbps, m.signal_strength_dbm::float8 AS signal,
                           t.traffic_volume_mb, m.connected_devices::float8 AS devices, t.packet_count::float8 AS packets
                    FROM measurements m
                    LEFT JOIN LATERAL (
                        SELECT o.traffic_volume_mb, o.packet_count FROM traffic_observations o
                        WHERE o.network_id = m.network_id AND o.observed_at = m.measured_at LIMIT 1) t ON TRUE
                    WHERE m.source = 'AGENT' AND m.measured_at >= ?
                    UNION ALL
                    SELECT r.received_at, 'H' || r.home_network_id, r.latency_ms, r.jitter_ms, r.packet_loss_pct,
                           NULL, r.signal_dbm::float8, NULL, r.device_count::float8, NULL
                    FROM agent_reports r
                    WHERE r.received_at >= ? AND r.latency_ms IS NOT NULL AND r.jitter_ms IS NOT NULL
                      AND r.packet_loss_pct IS NOT NULL
                ) s
                ORDER BY at DESC
                LIMIT ?""", this::sample, since, since, MAXIMUM_SAMPLES));
        Collections.reverse(samples);
        return samples;
    }

    private Sample sample(ResultSet rs, int row) throws SQLException {
        Double[] values = new Double[FEATURES.size()];
        for (int i = 0; i < values.length; i++) {
            double value = rs.getDouble(3 + i);
            values[i] = rs.wasNull() ? null : value;
        }
        return new Sample(rs.getTimestamp("at").toInstant(), rs.getString("network"), values);
    }

    private static AiTrainingStatus.Data summary(List<Sample> samples) {
        if (samples.isEmpty()) {
            return new AiTrainingStatus.Data(0, 0, 0, 0, null, null);
        }
        int team = (int) samples.stream().filter(s -> s.network().startsWith("T")).count();
        int networks = (int) samples.stream().map(Sample::network).distinct().count();
        return new AiTrainingStatus.Data(samples.size(), team, samples.size() - team, networks,
                samples.get(0).at(), samples.get(samples.size() - 1).at());
    }

    private List<AiTrainingStatus.Version> versions() {
        return jdbc.query("""
                SELECT v.id, v.version, v.algorithm, v.trained_at, u.username, v.samples, v.networks, v.data_from,
                       v.data_to, v.metrics::text AS metrics, v.decision, v.reason, v.active
                FROM ai_model_versions v LEFT JOIN users u ON u.id = v.trained_by
                ORDER BY v.trained_at DESC, v.id DESC
                LIMIT 30""", (rs, i) -> new AiTrainingStatus.Version(rs.getLong("id"), rs.getString("version"),
                rs.getString("algorithm"), rs.getTimestamp("trained_at").toInstant(), rs.getString("username"),
                rs.getInt("samples"), rs.getInt("networks"), rs.getTimestamp("data_from").toInstant(),
                rs.getTimestamp("data_to").toInstant(), readJson(rs.getString("metrics")), rs.getString("decision"),
                rs.getString("reason"), rs.getBoolean("active")));
    }

    private Instant nextAutomatic() {
        if (!automatic) {
            return null;
        }
        ZonedDateTime next = CronExpression.parse(cron).next(ZonedDateTime.now(clock.withZone(ZoneId.of(zone))));
        return next == null ? null : next.toInstant();
    }

    private boolean versionExists(String version) {
        return Objects.requireNonNullElse(jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_model_versions WHERE version = ?", Integer.class, version), 0) > 0;
    }

    private int countVersions() {
        return Objects.requireNonNullElse(jdbc.queryForObject("SELECT COUNT(*) FROM ai_model_versions", Integer.class), 0);
    }

    private JsonNode readJson(String text) {
        try {
            return json.readTree(text);
        } catch (JsonProcessingException e) {
            return json.createObjectNode();
        }
    }

    private static String trim(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
