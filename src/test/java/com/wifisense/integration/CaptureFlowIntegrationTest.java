package com.wifisense.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A technical capture end to end, through the real security filters and database: it is refused (and audited) while
 * the sensor cannot capture, an administrator asks for it with a purpose, the sensor picks it up, asks whether to stop
 * and delivers only counts; the statistics and the findings are stored apart from the inferences; an analyst can
 * neither ask for nor read a capture (and the attempt stays in the audit); the agent of a Mi Wi-Fi network and the
 * sensor of another network cannot touch it.
 */
@EnabledIfEnvironmentVariable(named = PostgresIntegrationTest.DATABASE_URL, matches = ".+")
@TestPropertySource(properties = {"wifisense.capture.poll-min-interval=0s", "wifisense.agent.min-interval=0s"})
class CaptureFlowIntegrationTest extends PostgresIntegrationTest {

    private static final Map<String, Object> CAN_CAPTURE = Map.of("captureConsent", true, "npcap", true,
            "pktmon", false, "elevated", false, "monitorMode", "UNSUPPORTED", "captureInterfaces", 2);

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper json;

    @Test
    void anAdministratorCapturesWithAPurposeAndTheSensorDeliversOnlyCounts() throws Exception {
        String admin = login(ADMIN);
        Sensor lab = sensor(admin, "Lab Captura ");
        String purpose = "Revisar la difusión ARP del laboratorio " + unique();

        // an old sensor that never said what its computer can do: refused, with the reason, and audited
        report(lab.key, "1.9.0", null);
        JsonNode refused = call(post(captures(lab)), admin, null, Map.of("purpose", purpose, "maxSeconds", 20), 409);
        assertThat(refused.toString()).contains("2.0.0");
        assertThat(lastAudit("CAPTURE_REQUESTED", lab.network)).containsEntry("outcome", "FAILED");

        // the computer allows it but captures were never allowed on it: still refused
        report(lab.key, "2.0.0", with(CAN_CAPTURE, "captureConsent", false));
        assertThat(call(get(captures(lab) + "/capabilities"), admin, null, null, 200).get("canCapture").asBoolean())
                .isFalse();

        report(lab.key, "2.0.0", CAN_CAPTURE);
        JsonNode capabilities = call(get(captures(lab) + "/capabilities"), admin, null, null, 200);
        assertThat(capabilities.get("canCapture").asBoolean()).isTrue();
        assertThat(capabilities.get("scenarios").findValuesAsText("code"))
                .containsExactly("LOCAL_HOST", "SHARED_INTERFACE", "MONITOR_MODE", "ROUTER");

        // limits above what the server allows are a 400; a purpose is required
        call(post(captures(lab)), admin, null, Map.of("purpose", purpose, "maxSeconds", 301), 400);
        call(post(captures(lab)), admin, null, Map.of("maxSeconds", 20), 400);

        long id = call(post(captures(lab)), admin, null, Map.of("purpose", purpose, "maxSeconds", 20,
                "maxPackets", 5000), 202).get("id").asLong();
        assertThat(lastAudit("CAPTURE_REQUESTED", lab.network)).containsEntry("outcome", "SUCCESS")
                .containsEntry("actor_username", ADMIN).containsEntry("target_id", id);
        assertThat((String) lastAudit("CAPTURE_REQUESTED", lab.network).get("detail")).contains(purpose);
        call(post(captures(lab)), admin, null, Map.of("purpose", purpose, "maxSeconds", 20), 409);  // one at a time

        // the next report only says that there is a capture; the sensor takes it with its limits
        assertThat(report(lab.key, "2.0.0", CAN_CAPTURE).get("captureId").asLong()).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT status FROM traffic_captures WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
        JsonNode task = call(get("/api/agent/captures/next"), null, lab.key, null, 200);
        assertThat(task.get("captureId").asLong()).isEqualTo(id);
        assertThat(task.get("maxSeconds").asInt()).isEqualTo(20);
        assertThat(task.get("maxPackets").asInt()).isEqualTo(5000);
        call(get("/api/agent/captures/next"), null, lab.key, null, 204);
        assertThat(call(get("/api/agent/captures/" + id + "/state"), null, lab.key, null, 200).get("stop").asBoolean())
                .isFalse();

        // a result that does not add up is refused and the capture keeps waiting for a real one
        Map<String, Object> result = result(id);
        call(post("/api/agent/captures"), null, lab.key, with(result, "packetCount", 10), 400);
        call(post("/api/agent/captures"), null, lab.key, result, 200);
        call(post("/api/agent/captures"), null, lab.key, result, 409);  // a finished capture cannot be delivered again

        JsonNode detail = call(get(captures(lab) + "/" + id), admin, null, null, 200);
        assertThat(detail.at("/capture/status").asText()).isEqualTo("COMPLETED");
        assertThat(detail.at("/capture/purpose").asText()).isEqualTo(purpose);
        assertThat(detail.at("/totals/packets").asInt()).isEqualTo(1200);
        assertThat(detail.at("/totals/droppedByTool").asInt()).isEqualTo(3);
        assertThat(detail.get("protocols").findValuesAsText("protocol")).contains("ARP", "TCP");
        assertThat(detail.get("observed").findValuesAsText("source")).isNotEmpty().containsOnly("RULES");
        assertThat(detail.get("scope").asText()).contains("No incluye");
        assertThat(detail.toString()).doesNotContain("192.168.").doesNotContain(":ab:");
        assertThat(lastAudit("CAPTURE_VIEWED", lab.network)).containsEntry("outcome", "SUCCESS");
        assertThat(call(get(captures(lab)), admin, null, null, 200).findValuesAsText("status")).contains("COMPLETED");
        assertThat(call(get("/api/admin/audit?action=CAPTURE_REQUESTED"), admin, null, null, 200).get("items")
                .findValuesAsText("action")).containsOnly("CAPTURE_REQUESTED");
    }

    @Test
    void aStoppedCaptureTellsTheSensorToStopAndKeepsWhatWasCaptured() throws Exception {
        String admin = login(ADMIN);
        Sensor lab = sensor(admin, "Lab Detener ");
        report(lab.key, "2.0.0", CAN_CAPTURE);
        long id = call(post(captures(lab)), admin, null, Map.of("purpose", "Medir el tráfico de difusión en la sala",
                "maxSeconds", 60), 202).get("id").asLong();
        call(get("/api/agent/captures/next"), null, lab.key, null, 200);

        call(post(captures(lab) + "/" + id + "/cancel"), admin, null, null, 200);
        assertThat(call(get("/api/agent/captures/" + id + "/state"), null, lab.key, null, 200).get("stop").asBoolean())
                .isTrue();
        call(post("/api/agent/captures"), null, lab.key, with(result(id), "stopReason", "CANCELLED"), 200);
        JsonNode detail = call(get(captures(lab) + "/" + id), admin, null, null, 200);
        assertThat(detail.at("/capture/stopReason").asText()).isEqualTo("CANCELLED");
        assertThat(detail.at("/totals/packets").asInt()).isEqualTo(1200);
        assertThat(lastAudit("CAPTURE_CANCELLED", lab.network)).containsEntry("outcome", "SUCCESS");
    }

    @Test
    void onlyAnAdministratorAndOnlyTheSensorOfThatNetworkTouchACapture() throws Exception {
        String admin = login(ADMIN);
        Sensor lab = sensor(admin, "Lab Permisos ");
        Sensor other = sensor(admin, "Lab Otro ");
        report(lab.key, "2.0.0", CAN_CAPTURE);
        report(other.key, "2.0.0", CAN_CAPTURE);
        long id = call(post(captures(lab)), admin, null, Map.of("purpose", "Comprobar permisos de las capturas",
                "maxSeconds", 10), 202).get("id").asLong();

        String analystName = "Ana.Cap" + unique();
        long analystId = call(post("/api/users"), admin, null,
                Map.of("username", analystName, "password", PASSWORD, "role", "ANALYST"), 201).get("id").asLong();
        try {
            String analyst = login(analystName);
            call(get(captures(lab)), analyst, null, null, 403);
            call(get(captures(lab) + "/" + id), analyst, null, null, 403);
            call(post(captures(lab)), analyst, null, Map.of("purpose", "Una captura que no le corresponde"), 403);
            call(get("/api/admin/audit"), analyst, null, null, 403);
            Map<String, Object> denied = jdbc.queryForMap("SELECT outcome, actor_role, detail FROM audit_events "
                    + "WHERE actor_username = ? AND action = 'CAPTURE_REQUESTED' ORDER BY id DESC LIMIT 1", analystName);
            assertThat(denied).containsEntry("outcome", "DENIED").containsEntry("actor_role", "ANALYST");
            assertThat((String) denied.get("detail")).contains("/sensor/captures");
        } finally {
            call(delete("/api/users/" + analystId), admin, null, null, 204);
        }

        // the sensor of another network gets "not found": captures of other networks do not exist for it
        call(get("/api/agent/captures/" + id + "/state"), null, other.key, null, 404);
        call(post("/api/agent/captures"), null, other.key, result(id), 404);
        // and a capture is read through its own network only
        call(get(captures(other) + "/" + id), admin, null, null, 404);

        // the agent of a Mi Wi-Fi network has no captures at all; a wrong key gets 401
        String user = "Beto.Cap" + unique();
        String token = call(post("/api/auth/register"), null, null,
                Map.of("username", user, "password", PASSWORD, "email", user + "@ejemplo.com"), 201).get("token").asText();
        try {
            long home = call(post("/api/my/networks"), token, null, Map.of("name", "Casa de Beto"), 201).get("id").asLong();
            String code = call(post("/api/my/networks/" + home + "/agent/pairing"), token, null, null, 201)
                    .get("code").asText();
            String homeKey = call(post("/api/agent/pair"), null, null, Map.of("code", code), 200).get("key").asText();
            call(get("/api/agent/captures/next"), null, homeKey, null, 404);
            call(post("/api/agent/captures"), null, homeKey, result(id), 404);
            call(get(captures(lab)), token, null, null, 404);  // for an end user team features do not exist
        } finally {
            jdbc.update("DELETE FROM users WHERE username = ?", user);
        }
        call(get("/api/agent/captures/next"), null, "wsa_" + "x".repeat(43), null, 401);

        assertThat(jdbc.queryForObject("SELECT status FROM traffic_captures WHERE id = ?", String.class, id))
                .isEqualTo("PENDING");
    }

    private record Sensor(long network, String key) {
    }

    private Sensor sensor(String admin, String prefix) throws Exception {
        String place = prefix + unique();
        long location = call(post("/api/locations"), admin, null, Map.of("name", place, "city", "Pasto"), 201)
                .get("id").asLong();
        long zone = call(post("/api/locations/" + location + "/zones"), admin, null,
                Map.of("name", "Piso 1", "floor", 1), 201).get("id").asLong();
        long network = call(post("/api/networks"), admin, null, Map.of("zoneId", zone, "ssid", place,
                "bssid", randomMac(), "frequencyBand", "5GHz", "channel", 36, "securityType", "WPA3",
                "dataSourceType", "AGENT"), 201).get("id").asLong();
        String code = call(post("/api/networks/" + network + "/sensor/pairing"), admin, null, null, 201)
                .get("code").asText();
        return new Sensor(network, call(post("/api/agent/pair"), null, null, Map.of("code", code), 200)
                .get("key").asText());
    }

    private JsonNode report(String key, String version, Map<String, Object> capabilities) throws Exception {
        Map<String, Object> body = new HashMap<>(Map.of("agentVersion", version, "platform", "Windows 11",
                "ssid", "Laboratorio", "latencyMs", 21.0, "packetLossPct", 0.0, "deviceCount", 3));
        if (capabilities != null) {
            body.put("capabilities", capabilities);
        }
        return call(post("/api/agent/reports"), null, key, body, 201);
    }

    /** What an agent 2.0.0 sends: only counts, never an address, a MAC or packet content. */
    private static Map<String, Object> result(long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("captureId", id);
        result.put("status", "COMPLETED");
        result.put("stopReason", "DURATION");
        result.put("captureTool", "NPCAP");
        result.put("interfaceName", "Wi-Fi");
        result.put("agentVersion", "2.0.0");
        result.put("durationMs", 20_000);
        result.put("packetCount", 1200);
        result.put("byteCount", 480_000L);
        result.put("droppedByTool", 3);
        result.put("malformedPackets", 0);
        result.put("inboundBytes", 300_000L);
        result.put("outboundBytes", 150_000L);
        result.put("broadcastPackets", 420);
        result.put("multicastPackets", 80);
        result.put("distinctLocalHosts", 9);
        result.put("distinctRemoteHosts", 14);
        result.put("protocols", List.of(Map.of("protocol", "ARP", "packets", 400, "bytes", 24_000),
                Map.of("protocol", "TCP", "packets", 600, "bytes", 400_000),
                Map.of("protocol", "DNS", "packets", 200, "bytes", 56_000)));
        result.put("ports", List.of(Map.of("transport", "TCP", "port", 443, "packets", 550, "bytes", 390_000)));
        result.put("intervals", List.of(Map.of("second", 0, "packets", 60, "bytes", 24_000),
                Map.of("second", 1, "packets", 58, "bytes", 23_000)));
        return result;
    }

    private Map<String, Object> lastAudit(String action, long network) {
        return jdbc.queryForMap("SELECT outcome, actor_username, target_id, detail FROM audit_events "
                + "WHERE action = ? AND network_id = ? ORDER BY id DESC LIMIT 1", action, network);
    }

    private static String captures(Sensor sensor) {
        return "/api/networks/" + sensor.network + "/sensor/captures";
    }

    private String login(String username) throws Exception {
        return call(post("/api/auth/login"), null, null, Map.of("username", username, "password", PASSWORD), 200)
                .get("token").asText();
    }

    private static Map<String, Object> with(Map<String, Object> map, String key, Object value) {
        Map<String, Object> copy = new HashMap<>(map);
        copy.put(key, value);
        return copy;
    }

    private JsonNode call(MockHttpServletRequestBuilder request, String token, String agentKey, Object body,
                          int expected) throws Exception {
        request.header("X-Forwarded-For", "10.25." + ThreadLocalRandom.current().nextInt(256) + "."
                + ThreadLocalRandom.current().nextInt(256));
        if (token != null) {
            request.header(AUTHORIZATION, "Bearer " + token);
        }
        if (agentKey != null) {
            request.header("X-Agent-Key", agentKey);
        }
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(body));
        }
        String response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse()
                .getContentAsString();
        return response.isEmpty() ? json.nullNode() : json.readTree(response);
    }

    private static String randomMac() {
        byte[] bytes = new byte[6];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.ofDelimiter(":").withUpperCase().formatHex(bytes);
    }
}
